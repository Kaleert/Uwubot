package pro.kaleert.uwubot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pro.kaleert.uwubot.command.RaspCommand;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.entity.SubjectAlias;
import pro.kaleert.uwubot.repository.SubjectAliasRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleDiffService {

    private final SubjectAliasRepository aliasRepository;

    public String generateDiffReport(Long userId, String groupName, List<Lesson> oldLessons, List<Lesson> newLessons, LocalDate weekStart) {
        if (oldLessons.isEmpty()) return "📅 <b>Новое расписание для " + groupName + "</b>\n\nПроверь /rasp";

        Map<String, String> aliases = (userId != null) 
            ? aliasRepository.findAllByUserId(userId).stream().collect(Collectors.toMap(a -> a.getOriginalName().toLowerCase(), SubjectAlias::getAliasName))
            : Collections.emptyMap();

        Map<DayOfWeek, List<Lesson>> oldByDay = groupLessons(oldLessons);
        Map<DayOfWeek, List<Lesson>> newByDay = groupLessons(newLessons);

        Set<DayOfWeek> changedDays = new TreeSet<>();
        Set<DayOfWeek> allDays = new HashSet<>();
        allDays.addAll(oldByDay.keySet());
        allDays.addAll(newByDay.keySet());

        for (DayOfWeek day : allDays) {
            List<Lesson> dayOld = oldByDay.getOrDefault(day, Collections.emptyList());
            List<Lesson> dayNew = newByDay.getOrDefault(day, Collections.emptyList());
            if (!dayOld.equals(dayNew)) changedDays.add(day);
        }

        if (changedDays.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("<b>🔔 Изменения в расписании " + groupName + "</b>\n\n");
        for (DayOfWeek day : changedDays) {
            List<Lesson> dayOld = oldByDay.getOrDefault(day, Collections.emptyList());
            List<Lesson> dayNew = newByDay.getOrDefault(day, Collections.emptyList());
            
            sb.append(formatDayHeader(day, weekStart)).append("\n"); // Передаем дату
            
            if (dayOld.isEmpty()) {
                sb.append(formatDayStandard(dayNew, aliases)).append("\n");
            } else {
                sb.append(formatDayDiff(dayOld, dayNew, aliases)).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatDayDiff(List<Lesson> oldList, List<Lesson> newList, Map<String, String> aliases) {
        StringBuilder sb = new StringBuilder();
        int maxOld = oldList.stream().mapToInt(Lesson::getLessonNumber).max().orElse(0);
        int maxNew = newList.stream().mapToInt(Lesson::getLessonNumber).max().orElse(0);
        int limit = Math.max(5, Math.max(maxOld, maxNew));
        Map<Integer, String> oldMap = oldList.stream().collect(Collectors.toMap(Lesson::getLessonNumber, Lesson::getRawText));
        Map<Integer, String> newMap = newList.stream().collect(Collectors.toMap(Lesson::getLessonNumber, Lesson::getRawText));

        for (int i = 1; i <= limit; i++) {
            String oldText = oldMap.get(i);
            String newText = newMap.get(i);
            if (oldText == null && newText == null) {
                if (i <= 5) sb.append(i).append(" | —\n");
                continue;
            }
            String line;
            if (Objects.equals(oldText, newText)) {
                line = formatLine(newText, aliases);
            } else if (newText == null) {
                line = "~~";
            } else if (oldText == null) {
                line = "<i>" + formatLine(newText, aliases) + "</i>";
            } else {
                line = "<i>" + formatLine(newText, aliases) + "</i>";
            }
            sb.append(i).append(" | ").append(line).append("\n");
        }
        return sb.toString();
    }

    private String formatDayStandard(List<Lesson> lessons, Map<String, String> aliases) {
        StringBuilder sb = new StringBuilder();
        int max = lessons.stream().mapToInt(Lesson::getLessonNumber).max().orElse(5);
        Map<Integer, String> map = lessons.stream().collect(Collectors.toMap(Lesson::getLessonNumber, Lesson::getRawText));
        for (int i = 1; i <= Math.max(5, max); i++) {
            sb.append(i).append(" | ").append(formatLine(map.getOrDefault(i, "—"), aliases)).append("\n");
        }
        return sb.toString();
    }
    
    private String formatLine(String raw, Map<String, String> aliases) {
        if (raw == null) return "—";
        return RaspCommand.formatLessonLine(raw, aliases, true);
    }
    
    private Map<DayOfWeek, List<Lesson>> groupLessons(List<Lesson> lessons) {
        return lessons.stream().collect(Collectors.groupingBy(Lesson::getDayOfWeek));
    }

    private String formatDayHeader(DayOfWeek day, LocalDate weekStart) {
        if (weekStart == null) weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate targetDate = weekStart.plusDays(day.getValue() - 1);
        
        String date = targetDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String ruName = switch (day) {
            case MONDAY -> "ПОНЕДЕЛЬНИК";
            case TUESDAY -> "ВТОРНИК";
            case WEDNESDAY -> "СРЕДА";
            case THURSDAY -> "ЧЕТВЕРГ";
            case FRIDAY -> "ПЯТНИЦА";
            case SATURDAY -> "СУББОТА";
            case SUNDAY -> "ВОСКРЕСЕНЬЕ";
        };
        return "<b>" + date + "  " + ruName + "</b>";
    }
}