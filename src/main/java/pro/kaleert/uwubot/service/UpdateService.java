package pro.kaleert.uwubot.service;

import com.kaleert.nyagram.client.NyagramClient;
import com.kaleert.nyagram.api.methods.send.SendMessage;
import pro.kaleert.uwubot.config.UwuBotConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.entity.ParsingMeta;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.repository.ParsingMetaRepository;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.service.parser.ScheduleBundle;
import pro.kaleert.uwubot.service.parser.ScheduleParserService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateService {

    private final ParsingMetaRepository metaRepository;
    private final LessonRepository lessonRepository;
    private final StudentRepository studentRepository;
    private final ScheduleParserService parserService;
    private final ScheduleDiffService diffService;
    private final NyagramClient botClient;
    private final UwuBotConfig properties;

    private static final String META_KEY = "schedule_file";
    private static final int MAX_RETRIES = 3;

    @Scheduled(fixedRateString = "${nyagram.scheduler.check-interval}")
    public void checkUpdates() {
        forceUpdate(status -> log.debug("Auto-Update: {}", status), false);
    }

    public void forceUpdate(Consumer<String> statusCallback) {
        forceUpdate(statusCallback, true);
    }

    public void forceUpdate(Consumer<String> statusCallback, boolean forceDownload) {
        statusCallback.accept("🔍 Поиск ссылки на сайте...");
        String fileUrl = null;
        
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Document doc = Jsoup.connect(properties.getScheduleUrl()).timeout(10000).get();
                
                org.jsoup.select.Elements links = doc.select("a[href$=.xlsx]");
                
                Element bestLink = findBestLink(links);
                
                if (bestLink != null) {
                    fileUrl = bestLink.attr("href");
                    if (!fileUrl.startsWith("http")) fileUrl = "https://edu.tatar.ru" + fileUrl;
                    log.debug("Выбрана ссылка: {} (Текст: {})", fileUrl, bestLink.text());
                    break;
                }
                
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                if (i == MAX_RETRIES - 1) statusCallback.accept("❌ Ошибка сайта: " + e.getMessage());
            }
        }

        if (fileUrl == null) {
            statusCallback.accept("❌ Подходящая ссылка не найдена.");
            return;
        }

        try {
            ParsingMeta meta = metaRepository.findById(META_KEY)
                    .orElse(new ParsingMeta(META_KEY, "", "", "", null, "", null, null));
            processNewFile(fileUrl, meta, statusCallback, forceDownload);
        } catch (Exception e) {
            log.error("Update failed", e);
            statusCallback.accept("❌ Ошибка: " + e.getMessage());
        }
    }

    private Element findBestLink(org.jsoup.select.Elements links) {
        Element bestLink = null;
        LocalDate maxDate = LocalDate.MIN;

        Pattern datePattern = Pattern.compile("(\\d{2})[._](\\d{2})[._](\\d{4})");

        for (Element link : links) {
            String raw = link.text() + " " + link.attr("href");
            
            if (!raw.toLowerCase().contains("расписание")) continue;

            Matcher m = datePattern.matcher(raw);
            if (m.find()) {
                try {
                    int day = Integer.parseInt(m.group(1));
                    int month = Integer.parseInt(m.group(2));
                    int year = Integer.parseInt(m.group(3));
                    
                    LocalDate date = LocalDate.of(year, month, day);
                    
                    if (date.isAfter(maxDate)) {
                        maxDate = date;
                        bestLink = link;
                    }
                } catch (Exception ignored) {}
            }
        }

        return bestLink != null ? bestLink : links.last();
    }

    public ScheduleBundle parseFileOnly(String url) throws Exception {
        java.net.URL rawUrl = new java.net.URL(url);
        String encodedUrl = new java.net.URI(rawUrl.getProtocol(), rawUrl.getUserInfo(), rawUrl.getHost(), rawUrl.getPort(), rawUrl.getPath(), rawUrl.getQuery(), null).toASCIIString();
        try (InputStream in = new URL(encodedUrl).openStream()) {
            return parserService.parse(in);
        }
    }

    @Transactional
    public void processNewFile(String url, ParsingMeta meta, Consumer<String> statusCallback, boolean force) throws Exception {
        statusCallback.accept("📥 Скачивание...");
        meta.setLastCheckTime(LocalDateTime.now());
        
        java.net.URL rawUrl = new java.net.URL(url);
        String encodedUrl = new java.net.URI(rawUrl.getProtocol(), rawUrl.getUserInfo(), rawUrl.getHost(), rawUrl.getPort(), rawUrl.getPath(), rawUrl.getQuery(), null).toASCIIString();

        byte[] fileBytes;
        try (InputStream in = new URL(encodedUrl).openStream()) {
            fileBytes = in.readAllBytes();
        }

        String currentHash = DigestUtils.md5DigestAsHex(fileBytes);
        boolean metaIsComplete = meta.getLastBellSchedule() != null && !meta.getLastBellSchedule().isEmpty();

        if (!force && currentHash.equals(meta.getLastFileHash()) && metaIsComplete) {
            log.debug("Файл не изменился.");
            statusCallback.accept("✅ Файл не изменился.");
            metaRepository.save(meta);
            return;
        }

        statusCallback.accept("⚙️ Парсинг...");
        try (InputStream parseStream = new ByteArrayInputStream(fileBytes)) {
            ScheduleBundle newBundle = parserService.parse(parseStream);
            List<Lesson> newLessons = newBundle.lessons();
            LocalDate newWeekStart = newBundle.weekStart();
            String newDateRange = newBundle.dateRangeString();
            String newBells = newBundle.bellSchedule();

            if (newLessons.isEmpty()) {
                statusCallback.accept("⚠️ Файл пуст.");
                return;
            }

            boolean isNewWeek = meta.getWeekStart() != null && !meta.getWeekStart().isEqual(newWeekStart);

            // Проверка звонков
            boolean bellsChanged = false;
            if (newBells != null && !newBells.isBlank()) {
                if (meta.getLastBellSchedule() != null && !meta.getLastBellSchedule().equals(newBells)) {
                    bellsChanged = true;
                }
            }

            Map<String, String> notifications = new HashMap<>();
            Set<String> affectedGroups = new HashSet<>();

            List<Lesson> oldLessons = lessonRepository.findAll();
            Map<String, List<Lesson>> oldMap = oldLessons.stream().collect(Collectors.groupingBy(Lesson::getGroupName));
            Map<String, List<Lesson>> newMap = newLessons.stream().collect(Collectors.groupingBy(Lesson::getGroupName));

            for (String group : newMap.keySet()) {
                List<Lesson> gNew = newMap.get(group);
                List<Lesson> gOld = oldMap.getOrDefault(group, Collections.emptyList());

                if (!isScheduleEqual(gOld, gNew)) {
                    affectedGroups.add(group);
                    if (isNewWeek) {
                        notifications.put(group, "📅 <b>Новое расписание!</b> (" + newDateRange + ")\nПроверь /rasp");
                    } else {
                        String diff = diffService.generateDiffReport(null, group, gOld, gNew, newWeekStart);
                        if (diff != null) notifications.put(group, diff);
                    }
                }
            }

            statusCallback.accept("💾 Сохранение...");
            lessonRepository.deleteAll();
            lessonRepository.saveAll(newLessons);
            
            meta.setLastFileUrl(url);
            meta.setLastFileHash(currentHash);
            meta.setLastDateRange(newDateRange);
            meta.setWeekStart(newWeekStart);
            meta.setLastBellSchedule(newBells);
            meta.setLastSuccessfulUpdate(LocalDateTime.now());
            metaRepository.save(meta);
            
            if (bellsChanged) {
                 String bellMsg = "🔔 <b>Изменилось расписание звонков!</b>\n\n" + newBells;
                 List<Student> allStudents = studentRepository.findAll();
                 for (Student s : allStudents) {
                     if (s.isNotificationsEnabled()) {
                         try {
                             botClient.execute(SendMessage.builder().chatId(s.getChatId().toString()).text(bellMsg).parseMode("HTML").build());
                         } catch (Exception ignored) {}
                     }
                 }
                 statusCallback.accept("🔔 Звонки обновлены.");
            }

            if (!affectedGroups.isEmpty()) {
                String type = isNewWeek ? "НОВАЯ НЕДЕЛЯ" : "ИЗМЕНЕНИЯ";
                statusCallback.accept("🔔 Рассылка (" + type + ") для " + affectedGroups.size() + " групп...");
                
                if (properties.getAdminId() != null) {
                    try {
                        String adminMsg = "📢 <b>Рассылка (" + type + "):</b>\nЗатронуто групп: " + affectedGroups.size() + "\n" + String.join(", ", affectedGroups);
                        if (adminMsg.length() > 4000) adminMsg = adminMsg.substring(0, 4000) + "...";
                        botClient.execute(SendMessage.builder()
                            .chatId(properties.getAdminId().toString())
                            .text(adminMsg)
                            .parseMode("HTML")
                            .build());
                    } catch (Exception ignored) {}
                }
                sendNotifications(notifications);
            }
            
            statusCallback.accept("✅ Готово!");
        }
    }

    private boolean isScheduleEqual(List<Lesson> list1, List<Lesson> list2) {
        if (list1.size() != list2.size()) return false;
        Comparator<Lesson> c = Comparator.comparing(Lesson::getDayOfWeek).thenComparingInt(Lesson::getLessonNumber);
        list1.sort(c); list2.sort(c);
        return list1.equals(list2);
    }

    private void sendNotifications(Map<String, String> notifications) {
        List<Student> students = studentRepository.findAll();
        for (Student s : students) {
            if (s.isNotificationsEnabled() && s.getSelectedGroup() != null) {
                String text = notifications.get(s.getSelectedGroup());
                if (text != null) {
                    try {
                        botClient.execute(SendMessage.builder().chatId(s.getChatId().toString()).text(text).parseMode("HTML").build());
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}