package pro.kaleert.uwubot.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import pro.kaleert.uwubot.entity.Lesson;
import pro.kaleert.uwubot.util.TextNormalizer;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleParserService {

    private static final Pattern GROUP_PATTERN = Pattern.compile("^[А-ЯA-Zа-я]{1,3}[- ]?\\d{2,4}[а-я]?$");
    private static final Pattern DAY_NAME_PATTERN = Pattern.compile(".*(ПОНЕДЕЛЬНИК|ВТОРНИК|СРЕДА|ЧЕТВЕРГ|ПЯТНИЦА|СУББОТА).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_EXTRACT_PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");

    public ScheduleBundle parse(InputStream inputStream) {
        List<Lesson> lessons = new ArrayList<>();
        String dateRangeString = "Unknown";
        LocalDate weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        Map<DayOfWeek, Map<Integer, String>> bellsByDay = new TreeMap<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int r = 0; r < 5; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String text = getMergedValue(sheet, r, 0); 
                Matcher m = DATE_EXTRACT_PATTERN.matcher(text);
                if (m.find()) {
                    try {
                        weekStart = LocalDate.parse(m.group(1), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                        dateRangeString = text.trim();
                    } catch (Exception ignored) {}
                    break;
                }
            }

            int headerRowIndex = findHeaderRow(sheet);
            if (headerRowIndex == -1) {
                return new ScheduleBundle(Collections.emptyList(), dateRangeString, weekStart, "");
            }

            Map<String, Integer> groupsMap = new HashMap<>();
            Row headerRow = sheet.getRow(headerRowIndex);
            for (Cell cell : headerRow) {
                String text = getCellText(cell);
                if (GROUP_PATTERN.matcher(text).matches()) {
                    groupsMap.put(TextNormalizer.normalizeGroup(text), cell.getColumnIndex());
                }
            }

            int startRow = headerRowIndex + 2;
            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String dayText = getMergedValue(sheet, r, 0);
                DayOfWeek currentDay = parseDayOfWeek(dayText);
                if (currentDay == null) continue;

                String lessonNumText = getMergedValue(sheet, r, 1);
                int lessonNum = parseLessonNumber(lessonNumText);
                if (lessonNum == 0) continue;
                
                String timeText = getMergedValue(sheet, r, 2).trim();
                if (!timeText.isEmpty()) {
                    timeText = timeText.replaceAll("\\s+", ""); 
                    if (timeText.length() == 9 && !timeText.contains("-")) { 
                         timeText = timeText.substring(0, 5) + "-" + timeText.substring(5);
                    }
                    
                    bellsByDay.computeIfAbsent(currentDay, k -> new TreeMap<>())
                              .put(lessonNum, timeText);
                }

                for (Map.Entry<String, Integer> entry : groupsMap.entrySet()) {
                    String groupName = entry.getKey();
                    int startCol = entry.getValue();

                    String s1 = getMergedValue(sheet, r, startCol);
                    String r1 = getMergedValue(sheet, r, startCol + 1);
                    String s2 = getMergedValue(sheet, r, startCol + 2);
                    String r2 = getMergedValue(sheet, r, startCol + 3);

                    String finalLessonText = buildLessonText(sheet, r, startCol, s1, r1, s2, r2);

                    if (finalLessonText != null) {
                        Lesson lesson = new Lesson();
                        lesson.setGroupName(groupName);
                        lesson.setDayOfWeek(currentDay);
                        lesson.setLessonNumber(lessonNum);
                        lesson.setRawText(finalLessonText);
                        lessons.add(lesson);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Parsing error", e);
            throw new RuntimeException(e);
        }

        String bellScheduleStr = formatBellSchedule(bellsByDay);

        return new ScheduleBundle(lessons, dateRangeString, weekStart, bellScheduleStr);
    }

    private String formatBellSchedule(Map<DayOfWeek, Map<Integer, String>> bellsByDay) {
        if (bellsByDay.isEmpty()) return "";

        Map<String, List<DayOfWeek>> grouped = new LinkedHashMap<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            if (!bellsByDay.containsKey(day)) continue;

            Map<Integer, String> dayBells = bellsByDay.get(day);
            String signature = dayBells.entrySet().stream()
                    .map(e -> e.getKey() + ". " + e.getValue())
                    .collect(Collectors.joining("\n"));

            grouped.computeIfAbsent(signature, k -> new ArrayList<>()).add(day);
        }

        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String, List<DayOfWeek>> entry : grouped.entrySet()) {
            List<DayOfWeek> days = entry.getValue();
            String schedule = entry.getKey();
            
            String header;
            if (days.size() == 1) {
                header = getDayName(days.get(0));
            } else if (days.size() == 6) {
                header = "ПОНЕДЕЛЬНИК - СУББОТА";
            } else if (areConsecutive(days)) {
                header = getDayName(days.get(0)) + " - " + getDayName(days.get(days.size() - 1));
            } else {
                header = days.stream().map(this::getDayName).collect(Collectors.joining(", "));
            }

            sb.append("🗓 <b>").append(header).append("</b>:\n")
              .append(schedule).append("\n\n");
        }

        return sb.toString().trim();
    }

    private boolean areConsecutive(List<DayOfWeek> days) {
        for (int i = 0; i < days.size() - 1; i++) {
            if (days.get(i).ordinal() + 1 != days.get(i + 1).ordinal()) return false;
        }
        return true;
    }

    private String getDayName(DayOfWeek day) {
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

    private String buildLessonText(Sheet sheet, int row, int startCol, String s1, String r1, String s2, String r2) {
        s1 = clean(s1); r1 = clean(r1); s2 = clean(s2); r2 = clean(r2);
        boolean isEmpty1 = s1.isEmpty();
        boolean isEmpty2 = s2.isEmpty();
        if (isEmpty1 && isEmpty2) return null;
        if (isMergedAcross(sheet, row, startCol, 3)) {
            if (r2.isEmpty()) return s1;
            return s1 + " (" + r2 + ")";
        }
        if (s1.equals(s2) && (r1.equals(r2) || r2.isEmpty())) {
            if (r1.isEmpty()) return s1;
            return s1 + " (" + r1 + ")";
        }
        StringBuilder sb = new StringBuilder();
        if (!isEmpty1) {
            sb.append(s1);
            if (!r1.isEmpty()) sb.append(" (").append(r1).append(")");
        } else {
            sb.append("—");
        }
        sb.append(" / ");
        if (!isEmpty2) {
            sb.append(s2);
            if (!r2.isEmpty()) sb.append(" (").append(r2).append(")");
        } else {
            sb.append("—");
        }
        return sb.toString();
    }
    private boolean isMergedAcross(Sheet sheet, int row, int col, int minWidth) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row, col) && region.getFirstRow() == row && region.getFirstColumn() == col) {
                return (region.getLastColumn() - region.getFirstColumn() + 1) >= minWidth;
            }
        }
        return false;
    }
    private String clean(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.equals("-")) return "";
        if (val.endsWith(".0")) return val.substring(0, val.length() - 2);
        return val;
    }
    private int findHeaderRow(Sheet sheet) {
        for (int r = 0; r < 20; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (GROUP_PATTERN.matcher(getCellText(cell)).matches()) return r;
            }
        }
        return -1;
    }
    private String getMergedValue(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                Row regionRow = sheet.getRow(region.getFirstRow());
                if (regionRow == null) return "";
                return getCellText(regionRow.getCell(region.getFirstColumn()));
            }
        }
        Row row = sheet.getRow(rowIdx);
        return row == null ? "" : getCellText(row.getCell(colIdx));
    }
    private String getCellText(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getStringCellValue();
                default -> "";
            };
        } catch (Exception e) { return ""; }
    }
    private DayOfWeek parseDayOfWeek(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = DAY_NAME_PATTERN.matcher(text);
        if (m.matches()) {
            String day = m.group(1).toUpperCase();
            return switch (day) {
                case "ПОНЕДЕЛЬНИК" -> DayOfWeek.MONDAY;
                case "ВТОРНИК" -> DayOfWeek.TUESDAY;
                case "СРЕДА" -> DayOfWeek.WEDNESDAY;
                case "ЧЕТВЕРГ" -> DayOfWeek.THURSDAY;
                case "ПЯТНИЦА" -> DayOfWeek.FRIDAY;
                case "СУББОТА" -> DayOfWeek.SATURDAY;
                default -> null;
            };
        }
        return null;
    }
    private int parseLessonNumber(String text) {
        text = clean(text);
        if (text.isEmpty()) return 0;
        try { return (int) Double.parseDouble(text); } catch (Exception e) { return 0; }
    }
}