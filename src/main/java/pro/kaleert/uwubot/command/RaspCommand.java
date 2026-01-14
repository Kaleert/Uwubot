package pro.kaleert.uwubot.command;

import com.kaleert.nyagram.api.methods.updatingmessages.EditMessageText;
import com.kaleert.nyagram.api.objects.message.Message;
import com.kaleert.nyagram.command.*;
import com.kaleert.nyagram.util.TextUtil;
import lombok.RequiredArgsConstructor;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.entity.ParsingMeta;
import pro.kaleert.uwubot.entity.Student;
import pro.kaleert.uwubot.entity.SubjectAlias;
import pro.kaleert.uwubot.repository.LessonRepository;
import pro.kaleert.uwubot.repository.ParsingMetaRepository;
import pro.kaleert.uwubot.repository.StudentRepository;
import pro.kaleert.uwubot.repository.SubjectAliasRepository;
import pro.kaleert.uwubot.service.GroupService;
import pro.kaleert.uwubot.service.UpdateService;
import pro.kaleert.uwubot.util.TextNormalizer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@BotCommand(value = "/rasp", description = "Показать расписание")
@RequiredArgsConstructor
public class RaspCommand {

    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final SubjectAliasRepository aliasRepository;
    private final UpdateService updateService;
    private final GroupService groupService;
    private final ParsingMetaRepository metaRepository;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^([А-ЯA-Z]{2,5}(\\.[А-ЯA-Z]{2,5})?(\\.\\d{1,2}){0,3}\\.?)\\s+(.*)");

    @CommandHandler(aliases = {"рп", "расписание"})
    public void showSchedule(CommandContext context, 
                             @CommandArgument(value = "group", required = false) String groupArg) {
        
        Long userId = context.getUserId();
        
        Student student = studentRepository.findById(userId).orElseGet(() -> {
            Student s = new Student();
            s.setUserId(userId);
            s.setChatId(context.getChatId());
            s.setShowCodes(false); 
            return s;
        });

        String targetGroup;
        if (groupArg != null && !groupArg.isBlank()) {
            try {
                targetGroup = groupService.resolveGroupName(groupArg);
            } catch (IllegalArgumentException e) {
                // Если база пуста, используем "сырую" нормализацию
                targetGroup = TextNormalizer.normalizeGroup(groupArg);
            }
        } else {
            if (student.getSelectedGroup() == null) {
                context.reply("⚠️ Группа не выбрана. Используй <code>/group [номер]</code>", "HTML");
                return;
            }
            targetGroup = student.getSelectedGroup();
        }

        List<Lesson> lessons = lessonRepository.findByGroupName(targetGroup);
        
        // Если уроков нет -> пробуем найти группу через Smart Search (вдруг опечатка в БД или профиле)
        if (lessons.isEmpty() && lessonRepository.count() > 0) {
            try {
                String smartGroup = groupService.resolveGroupName(targetGroup);
                if (!smartGroup.equals(targetGroup)) {
                    targetGroup = smartGroup;
                    lessons = lessonRepository.findByGroupName(targetGroup);
                    // Если это была своя группа, можно тихо обновить профиль
                    if (groupArg == null) {
                        student.setSelectedGroup(targetGroup);
                        studentRepository.save(student);
                    }
                }
            } catch (Exception ignored) {}
        }
        
        // Если ВСЁ ЕЩЕ пусто -> запускаем обновление (НО С ФЛАГОМ FALSE)
        if (lessons.isEmpty()) {
            final String groupToFind = targetGroup;
            Message statusMsg = context.reply("⏳ Нет данных для <b>" + groupToFind + "</b>. Проверяю обновление...", "HTML").join();
            
            CompletableFuture.runAsync(() -> {
                // 🔥 ВАЖНО: false означает "не скачивать, если хэш тот же"
                updateService.forceUpdate(status -> {
                    try {
                        context.getClient().execute(EditMessageText.builder()
                                .chatId(context.getChatId().toString())
                                .messageId(Math.toIntExact(statusMsg.getMessageId()))
                                .text(status)
                                .build());
                    } catch (Exception ignored) {}
                }, false); 
            }).thenRun(() -> {
                try {
                    // Еще раз пробуем найти после обновления
                    String refreshedGroup = groupService.resolveGroupName(groupToFind);
                    List<Lesson> newLessons = lessonRepository.findByGroupName(refreshedGroup);
                    if (newLessons.isEmpty()) {
                        context.reply("❌ Расписание не найдено даже в новом файле.", "HTML");
                    } else {
                        sendScheduleResult(context, student, refreshedGroup, newLessons);
                    }
                } catch (Exception e) {
                     context.reply("❌ Группа не найдена.");
                }
            });
            return;
        }

        sendScheduleResult(context, student, targetGroup, lessons);
    }

    private void sendScheduleResult(CommandContext context, Student student, String groupName, List<Lesson> lessons) {
        Map<String, String> userAliases = aliasRepository.findAllByUserId(context.getUserId()).stream()
                .collect(Collectors.toMap(a -> a.getOriginalName().toLowerCase(), SubjectAlias::getAliasName));

        ParsingMeta meta = metaRepository.findById("schedule_file").orElse(null);
        LocalDate weekStart = (meta != null && meta.getWeekStart() != null) 
                ? meta.getWeekStart() 
                : LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        String result = formatSchedule(groupName, lessons, userAliases, student.isShowCodes(), weekStart);
        context.reply(result, "HTML");
    }

    public static String formatSchedule(String group, List<Lesson> lessons, Map<String, String> aliases, boolean showCodes, LocalDate weekStart) {
        StringBuilder sb = new StringBuilder("Расписание для <b>" + group + "</b>\n\n");
        Map<DayOfWeek, List<Lesson>> byDay = lessons.stream().collect(Collectors.groupingBy(Lesson::getDayOfWeek));
        List<DayOfWeek> sortedDays = byDay.keySet().stream().sorted().toList();

        for (DayOfWeek day : sortedDays) {
            sb.append("<b>").append(getDateForDay(day, weekStart)).append("  ").append(getDayNameRu(day)).append("</b>\n");

            List<Lesson> dayLessons = byDay.get(day);
            int maxLesson = dayLessons.stream().mapToInt(Lesson::getLessonNumber).max().orElse(5);
            int limit = Math.max(5, maxLesson);

            for (int i = 1; i <= limit; i++) {
                int currentNum = i;
                String rawText = dayLessons.stream()
                        .filter(l -> l.getLessonNumber() == currentNum)
                        .map(Lesson::getRawText)
                        .findFirst()
                        .orElse("—");

                String formattedLine = formatLessonLine(rawText, aliases, showCodes);
                sb.append(i).append(" | ").append(formattedLine).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String getDateForDay(DayOfWeek targetDay, LocalDate weekStart) {
        if (weekStart == null) weekStart = LocalDate.now();
        LocalDate targetDate = weekStart.plusDays(targetDay.getValue() - 1);
        return targetDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    public static String formatLessonLine(String raw, Map<String, String> aliases, boolean showCodes) {
        if (raw.equals("—")) return raw;

        if (raw.contains(" / ")) {
            String[] parts = raw.split(" / ");
            LessonInfo info1 = parseLessonInfo(parts[0], aliases, showCodes);
            LessonInfo info2 = (parts.length > 1) ? parseLessonInfo(parts[1], aliases, showCodes) : new LessonInfo("—", "");

            if (info1.name.equals("—") && info2.name.equals("—")) return "—";

            if (info1.name.equals(info2.name) && !info1.name.equals("—")) {
                String room1 = info1.room.isEmpty() ? "" : "[" + info1.room + "]";
                String room2 = info2.room.isEmpty() ? "" : "[" + info2.room + "]";
                if (room1.equals(room2)) return formatSingle(info1);
                return info1.name + " " + room1 + " / " + room2;
            } else {
                return formatSingle(info1) + " / " + formatSingle(info2);
            }
        } else {
            return formatSingle(parseLessonInfo(raw, aliases, showCodes));
        }
    }

    private static String formatSingle(LessonInfo info) {
        if (info.name.equals("—")) return "—";
        if (info.room.isEmpty()) return info.name;
        return info.name + " [" + info.room + "]";
    }

    private static LessonInfo parseLessonInfo(String part, Map<String, String> aliases, boolean showCodes) {
        part = part.trim();
        if (part.equals("—") || part.isEmpty()) return new LessonInfo("—", "");
        String subjectFull = part;
        String room = "";
        if (part.endsWith(")")) {
            int openParen = part.lastIndexOf('(');
            if (openParen > 0) {
                subjectFull = part.substring(0, openParen).trim();
                room = part.substring(openParen + 1, part.length() - 1);
            }
        }
        String displayName = subjectFull;
        if (!showCodes) {
            Matcher m = PREFIX_PATTERN.matcher(subjectFull);
            if (m.matches()) {
                String cleanName = m.group(4).trim();
                if (cleanName.length() > 1) displayName = cleanName;
            }
        }
        String lookupName = displayName;
        if (showCodes) {
             Matcher m = PREFIX_PATTERN.matcher(subjectFull);
             if (m.matches()) lookupName = m.group(4).trim();
        }
        String alias = aliases.get(lookupName.toLowerCase());
        if (alias != null) {
            if (showCodes) {
                Matcher m = PREFIX_PATTERN.matcher(subjectFull);
                if (m.matches()) {
                    String code = m.group(1).trim();
                    displayName = code + " " + alias;
                } else {
                    displayName = alias;
                }
            } else {
                displayName = alias;
            }
        }
        return new LessonInfo(TextUtil.escapeHtml(displayName), TextUtil.escapeHtml(room));
    }
    
    private record LessonInfo(String name, String room) {}
    
    private static String getDayNameRu(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "ПОНЕДЕЛЬНИК";
            case TUESDAY -> "ВТОРНИК";
            case WEDNESDAY -> "СРЕДА";
            case THURSDAY -> "ЧЕТВЕРГ";
            case FRIDAY -> "ПЯТНИЦА";
            case SATURDAY -> "СУББОТА";
            case SUNDAY -> "ВОСКРЕСЕНЬЕ";
        };
    }
}