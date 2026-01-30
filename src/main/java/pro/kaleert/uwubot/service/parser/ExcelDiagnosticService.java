package pro.kaleert.uwubot.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExcelDiagnosticService {

    public File createDump(InputStream inputStream) {
        File tempFile;
        try {
            tempFile = File.createTempFile("excel_debug_", ".txt");
        } catch (Exception e) {
            throw new RuntimeException("Could not create temp file", e);
        }

        try (Workbook workbook = WorkbookFactory.create(inputStream);
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8))) {

            Sheet sheet = workbook.getSheetAt(0);

            writer.write("=== EXCEL DEEP DUMP ===\n");
            writer.write("Sheet: " + sheet.getSheetName() + "\n");
            writer.write("Last Row Index: " + sheet.getLastRowNum() + "\n");
            writer.write("Merged Regions: " + sheet.getNumMergedRegions() + "\n");
            writer.write("=======================\n\n");

            // Сканируем с запасом, чтобы найти скрытые данные
            int maxRow = sheet.getLastRowNum() + 5;

            for (int r = 0; r <= maxRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue; // Пропускаем полностью пустые строки
                }

                // Строим буфер строки, чтобы записать её только если в ней есть данные
                StringBuilder rowBuffer = new StringBuilder();
                boolean hasData = false;

                rowBuffer.append(String.format("--- [ROW %d] Height:%s ---\n", r, row.getHeight()));

                // Сканируем первые 60 колонок (обычно расписание не шире)
                int lastCell = Math.max(row.getLastCellNum(), 60);

                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c);
                    CellAddress addr = new CellAddress(r, c);
                    
                    String value = getCellText(cell);
                    String mergeInfo = getMergeInfo(sheet, r, c);
                    String styleInfo = getStyleInfo(cell);

                    // Если ячейка не пустая, или объединена, или имеет стиль (фон/границы)
                    if (!value.isBlank() || !mergeInfo.isEmpty() || !styleInfo.isEmpty()) {
                        hasData = true;
                        
                        String safeValue = value.replace("\n", "\\n").replace("\r", "");
                        
                        // Формат: [C5] 'Значение' | MERGED: 2x1 | STYLE: {BOLD, RED}
                        rowBuffer.append(String.format("   [%-4s] (c:%d) '%s'", 
                                addr.formatAsString(), c, safeValue));

                        if (!mergeInfo.isEmpty()) {
                            rowBuffer.append(" | ").append(mergeInfo);
                        }
                        if (!styleInfo.isEmpty()) {
                            rowBuffer.append(" | ").append(styleInfo);
                        }
                        rowBuffer.append("\n");
                    }
                }

                if (hasData) {
                    writer.write(rowBuffer.toString());
                    writer.write("\n");
                }
            }

            writer.write("\n=== END OF DUMP ===");
            return tempFile;

        } catch (Exception e) {
            log.error("Analysis failed", e);
            throw new RuntimeException("Analysis failed: " + e.getMessage());
        }
    }

    private String getMergeInfo(Sheet sheet, int row, int col) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(row, col)) {
                if (region.getFirstRow() == row && region.getFirstColumn() == col) {
                    // Это начало объединенной ячейки
                    int rows = region.getLastRow() - region.getFirstRow() + 1;
                    int cols = region.getLastColumn() - region.getFirstColumn() + 1;
                    return String.format("MERGE_START[%dx%d]->%s", rows, cols, 
                            new CellAddress(region.getLastRow(), region.getLastColumn()).formatAsString());
                } else {
                    // Это часть объединенной ячейки (обычно пустая)
                    return String.format("MERGED_IN(%s)", 
                            new CellAddress(region.getFirstRow(), region.getFirstColumn()).formatAsString());
                }
            }
        }
        return "";
    }

    private String getStyleInfo(Cell cell) {
        if (cell == null) return "";
        CellStyle style = cell.getCellStyle();
        if (style == null) return "";

        List<String> props = new ArrayList<>();
        Workbook wb = cell.getSheet().getWorkbook();
        
        // Font
        org.apache.poi.ss.usermodel.Font font = wb.getFontAt(style.getFontIndex());
        if (font.getBold()) props.add("BOLD");
        if (font.getItalic()) props.add("ITALIC");
        if (font.getColor() != org.apache.poi.ss.usermodel.Font.COLOR_NORMAL) props.add("COLORED_TEXT");

        // Fill / Background
        if (style.getFillPattern() != FillPatternType.NO_FILL) {
            Color color = style.getFillForegroundColorColor();
            if (color instanceof XSSFColor xssfColor) {
                String hex = xssfColor.getARGBHex();
                if (hex != null) props.add("BG:" + hex);
                else props.add("BG_COLOR");
            } else {
                props.add("BG_INDEX:" + style.getFillForegroundColor());
            }
        }

        // Borders
        if (style.getBorderBottom() != BorderStyle.NONE) props.add("B_BOTT");
        if (style.getBorderTop() != BorderStyle.NONE) props.add("B_TOP");
        if (style.getBorderLeft() != BorderStyle.NONE) props.add("B_LEFT");
        if (style.getBorderRight() != BorderStyle.NONE) props.add("B_RIGHT");

        // Alignment
        if (style.getAlignment() == HorizontalAlignment.CENTER) props.add("CENTER");
        if (style.getRotation() != 0) props.add("ROT:" + style.getRotation());

        if (props.isEmpty()) return "";
        return "{" + String.join(",", props) + "}";
    }

    private String getCellText(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toString();
                    }
                    yield String.valueOf(cell.getNumericCellValue());
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield "FORMULA=" + cell.getCellFormula() + " -> " + cell.getStringCellValue();
                    } catch (Exception e) {
                        yield "FORMULA=" + cell.getCellFormula() + " -> " + cell.getNumericCellValue();
                    }
                }
                default -> "";
            };
        } catch (Exception e) {
            return "[ERR]";
        }
    }
}