package pro.kaleert.uwubot.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ExcelStructureAnalyzer {

    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("^[А-ЯA-Zа-яa-z]{1,3}[- ]?\\d{2,4}[а-я]?$");
    private static final Pattern DAY_PATTERN = Pattern.compile(".*(ПОНЕДЕЛЬНИК|ВТОРНИК|СРЕДА|ЧЕТВЕРГ|ПЯТНИЦА|СУББОТА).*", Pattern.CASE_INSENSITIVE);

    public FileStructure analyze(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        FileStructure structure = new FileStructure();
        
        for (Row row : sheet) {
            if (row.getRowNum() > 20) break; 

            for (Cell cell : row) {
                String text = getCellText(cell).trim();
                
                if (GROUP_NAME_PATTERN.matcher(text).matches()) {
                    int colIndex = cell.getColumnIndex();
                    int colspan = getColSpan(sheet, row.getRowNum(), colIndex);
                    
                    structure.addGroup(text, colIndex, colspan);
                    structure.setHeaderRowIndex(row.getRowNum());
                }
            }
            
            if (structure.getGroups().size() > 2) break;
        }

        if (structure.getHeaderRowIndex() != -1) {
            int startRow = structure.getHeaderRowIndex() + 1;
            for (int r = startRow; r < Math.min(startRow + 100, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                for (int c = 0; c < 5; c++) {
                    Cell cell = row.getCell(c);
                    String text = getCellText(cell).trim().toUpperCase();

                    if (structure.getDayColumnIndex() == -1 && DAY_PATTERN.matcher(text).matches()) {
                        structure.setDayColumnIndex(c);
                        log.info("Found DAY column at index: {} (value: '{}')", c, text);
                    }

                    if (structure.getTimeColumnIndex() == -1 && text.matches(".*\\d{1,2}[:.]\\d{2}.*")) {
                        structure.setTimeColumnIndex(c);
                        log.info("Found TIME column at index: {} (value: '{}')", c, text);
                    }
                }
            }
        }

        if (structure.getDayColumnIndex() == -1 && structure.getTimeColumnIndex() != -1) {
            int assumedDayCol = 0;
            
            log.warn("Day column not found by regex. Assuming column {} based on Time column location.", assumedDayCol);
            structure.setDayColumnIndex(assumedDayCol);
        }
        
        if (structure.getDayColumnIndex() == -1 && !structure.getGroups().isEmpty()) {
             structure.setDayColumnIndex(0);
             log.warn("Columns not identified perfectly. Forcing DayColumn = 0");
        }

        log.info("Analysis Complete: {}", structure);
        return structure;
    }

    public String getCellText(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toString();
                    }
                    yield String.valueOf((int) cell.getNumericCellValue());
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e) {
                        yield String.valueOf(cell.getNumericCellValue());
                    }
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private int getColSpan(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIdx, colIdx)) {
                return region.getLastColumn() - region.getFirstColumn() + 1;
            }
        }
        return 1;
    }

    @lombok.Data
    public static class FileStructure {
        private int headerRowIndex = -1;
        private int dayColumnIndex = -1;
        private int timeColumnIndex = -1;
        private Map<String, GroupColumnRange> groups = new HashMap<>();

        public void addGroup(String name, int startCol, int width) {
            groups.put(name, new GroupColumnRange(startCol, width));
        }

        public boolean isComplete() {
            return dayColumnIndex != -1 && !groups.isEmpty();
        }
    }

    public record GroupColumnRange(int startCol, int width) {}
}