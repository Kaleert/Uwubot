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

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern GROUP_PATTERN = Pattern.compile("^[А-ЯA-Zа-я]{1,2}[- ]?\\d{2,4}[а-я]?$");

    public ScheduleBundle parse(InputStream inputStream) {
        List<Lesson> lessons = new ArrayList<>();
        String dateRangeString = "Unknown";
        LocalDate weekStart = LocalDate.now();
        Map<Integer, String> bellMap = new TreeMap<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int r = 0; r < 5; r++) {
                String rowText = getMergedValue(sheet, r, 0);
                Matcher m = DATE_RANGE_PATTERN.matcher(rowText);
                if (m.find()) {
                    dateRangeString = rowText.trim();
                    try {
                        weekStart = LocalDate.parse(m.group(1), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    } catch (Exception ignored) {}
                    break;
                }
            }

            int headerRowIndex = findHeaderRow(sheet);
            if (headerRowIndex == -1) return new ScheduleBundle(Collections.emptyList(), "Error", weekStart, "");

            Map<String, Integer> groupsMap = new LinkedHashMap<>();
            Row headerRow = sheet.getRow(headerRowIndex);
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String text = getCellText(headerRow.getCell(c));
                if (GROUP_PATTERN.matcher(text).matches()) {
                    groupsMap.put(TextNormalizer.normalizeGroup(text), c);
                }
            }

            int startRow = headerRowIndex + 2; 
            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String dayText = getMergedValue(sheet, r, 0);
                String lessonNumText = getMergedValue(sheet, r, 1);
                String timeText = getMergedValue(sheet, r, 2);

                DayOfWeek day = parseDay(dayText);
                int lessonNum = parseLessonNum(lessonNumText);

                if (day == null || lessonNum == 0) continue;
                if (!timeText.isBlank()) bellMap.putIfAbsent(lessonNum, clean(timeText));

                for (Map.Entry<String, Integer> entry : groupsMap.entrySet()) {
                    String groupName = entry.getKey();
                    int col = entry.getValue();

                    String s1 = clean(getCellText(sheet, r, col));
                    String r1 = clean(getCellText(sheet, r, col + 1));
                    String s2 = clean(getCellText(sheet, r, col + 2));
                    String r2 = clean(getCellText(sheet, r, col + 3));
                    
                    String t1 = clean(getCellText(sheet, r + 1, col));
                    String t2 = clean(getCellText(sheet, r + 1, col + 2));

                    if (s1.isEmpty() && s2.isEmpty()) continue;

                    boolean isWideSplit = false;
                    if (!s1.isEmpty() && r1.isEmpty() && s2.isEmpty() && !r2.isEmpty()) {
                        r1 = r2; 
                        isWideSplit = true;
                    }

                    boolean isMerged = isMergedAcross(sheet, r, col);
                    boolean isIdentical = s1.equals(s2) && r1.equals(r2) && !s1.isEmpty();
                    
                    String finalLesson;
                    String finalTeacher;

                    if (isMerged || isIdentical || isWideSplit) {
                        finalLesson = formatItem(s1.isEmpty() ? s2 : s1, r1.isEmpty() ? r2 : r1);
                        finalTeacher = s1.isEmpty() ? t2 : t1;
                    } 
                    else {
                        String part1 = formatItem(s1, r1);
                        String part2;

                        if (!s1.isEmpty() && s1.equals(s2)) {
                            if (!r2.isEmpty()) {
                                part2 = "[" + r2 + "]";
                            } else {
                                part2 = s2; 
                            }
                        } else {
                            part2 = formatItem(s2, r2);
                        }
                        
                        finalLesson = part1 + " / " + part2;
                        
                        if (t1.equals(t2) && !t1.isEmpty()) finalTeacher = t1;
                        else {
                             String teach1 = t1.isEmpty() ? "—" : t1;
                             String teach2 = t2.isEmpty() ? "—" : t2;
                             finalTeacher = teach1 + " / " + teach2;
                        }
                    }

                    Lesson lesson = new Lesson();
                    lesson.setGroupName(groupName);
                    lesson.setDayOfWeek(day);
                    lesson.setLessonNumber(lessonNum);
                    lesson.setRawText(finalLesson);
                    lesson.setTeacher(finalTeacher);
                    lessons.add(lesson);
                }
            }
        } catch (Exception e) {
            log.error("Parsing error", e);
            throw new RuntimeException(e);
        }

        String bellSchedule = bellMap.entrySet().stream()
                .map(e -> e.getKey() + ". " + e.getValue())
                .collect(Collectors.joining("\n"));

        return new ScheduleBundle(lessons, dateRangeString, weekStart, bellSchedule);
    }

    private String formatItem(String subject, String room) {
        if (subject == null || subject.isEmpty()) return "—";
        if (room == null || room.isEmpty()) return subject;
        return subject + " [" + room + "]";
    }
    
    private String clean(String text) {
        if (text == null) return "";
        text = text.replace('\u00A0', ' ').trim();
        text = text.replaceAll("\\s+", " ");
        
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        if (text.equals("-") || text.equals("'") || text.equals("`") || text.equals(".")) return "";
        return text;
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
                Row r = sheet.getRow(region.getFirstRow());
                if (r == null) return "";
                return getCellText(r.getCell(region.getFirstColumn()));
            }
        }
        return getCellText(sheet.getRow(rowIdx).getCell(colIdx));
    }
    
    private String getCellText(Sheet sheet, int r, int c) {
        Row row = sheet.getRow(r);
        return (row == null) ? "" : getCellText(row.getCell(c));
    }

    private String getCellText(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getStringCellValue().trim();
                default -> "";
            };
        } catch (Exception e) { return ""; }
    }

    private boolean isMergedAcross(Sheet sheet, int row, int col) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row, col)) {
                return (region.getLastColumn() - region.getFirstColumn()) > 1;
            }
        }
        return false;
    }

    private DayOfWeek parseDay(String text) {
        if (text == null) return null;
        text = text.toLowerCase();
        if (text.contains("понедельник")) return DayOfWeek.MONDAY;
        if (text.contains("вторник")) return DayOfWeek.TUESDAY;
        if (text.contains("среда")) return DayOfWeek.WEDNESDAY;
        if (text.contains("четверг")) return DayOfWeek.THURSDAY;
        if (text.contains("пятница")) return DayOfWeek.FRIDAY;
        if (text.contains("суббота")) return DayOfWeek.SATURDAY;
        return null;
    }

    private int parseLessonNum(String text) {
        return (text == null || text.isBlank()) ? 0 : (int) Double.parseDouble(clean(text));
    }
}