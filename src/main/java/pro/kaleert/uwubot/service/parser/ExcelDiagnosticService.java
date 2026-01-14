package pro.kaleert.uwubot.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExcelDiagnosticService {

    public void dumpStructure(InputStream inputStream, String outputFileName) {
        log.info("🔍 STARTING EXCEL DUMP -> {}", outputFileName);
        
        try (Workbook workbook = WorkbookFactory.create(inputStream);
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName, StandardCharsets.UTF_8))) {

            Sheet sheet = workbook.getSheetAt(0);
            
            writer.write("=== EXCEL STRUCTURE DUMP ===\n");
            writer.write("Total Rows: " + sheet.getLastRowNum() + "\n\n");

            for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 100); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    writer.write(String.format("[ROW %d] IS NULL\n", r));
                    continue;
                }

                writer.write(String.format("--- [ROW %d] ---\n", r));

                for (int c = 0; c < 150; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;

                    String value = getCellText(cell);
                    if (value.isBlank()) continue;

                    String mergeInfo = getMergeInfo(sheet, r, c);
                    
                    String colorInfo = getColorInfo(cell);

                    writer.write(String.format("   COL %d: '%s' %s %s\n", 
                            c, 
                            value.replace("\n", "\\n"), 
                            mergeInfo,
                            colorInfo
                    ));
                }
            }
            
            log.info("✅ DUMP SAVED SUCCESSFULLY TO {}", outputFileName);

        } catch (Exception e) {
            log.error("Failed to dump excel", e);
        }
    }

    private String getMergeInfo(Sheet sheet, int row, int col) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(row, col)) {
                if (region.getFirstRow() == row && region.getFirstColumn() == col) {
                    return String.format("[MERGED: %d rows x %d cols -> ends at R%d:C%d]", 
                            region.getLastRow() - region.getFirstRow() + 1,
                            region.getLastColumn() - region.getFirstColumn() + 1,
                            region.getLastRow(),
                            region.getLastColumn());
                } else {
                    return "[PART OF MERGE]";
                }
            }
        }
        return "";
    }

    private String getCellText(Cell cell) {
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
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
            return "[ERR]";
        }
    }
    
    private String getColorInfo(Cell cell) {
        CellStyle style = cell.getCellStyle();
        if (style == null) return "";
        
        if (style.getFillPattern() != FillPatternType.NO_FILL) {
            return "[HAS_BG_COLOR]"; 
        }
        return "";
    }
}
