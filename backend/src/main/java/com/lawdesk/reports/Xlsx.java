package com.lawdesk.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Excel ({@code .xlsx}) for report exports (LD-034), written with fastexcel. Text is stored as text cells, which a
 * spreadsheet never evaluates as formulas; dates are date cells, counts numbers. An Arabic export is a right-to-left
 * sheet.
 */
final class Xlsx {

    private static final int MAX_SHEET_NAME = 31;

    private Xlsx() {
    }

    static byte[] write(ExportTable table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "LawDesk", "1.0");
            Worksheet sheet = workbook.newWorksheet(sheetName(table.title()));
            if (table.labels().rightToLeft()) {
                sheet.rightToLeft();
            }
            List<String> header = table.header();
            for (int column = 0; column < header.size(); column++) {
                sheet.value(0, column, header.get(column));
                sheet.width(column, 22);
            }
            sheet.range(0, 0, 0, header.size() - 1).style().bold().fillColor("E8EEF7").set();
            sheet.freezePane(0, 1);
            int r = 1;
            for (List<Object> row : table.rows()) {
                for (int column = 0; column < row.size(); column++) {
                    Object cell = row.get(column);
                    if (cell instanceof LocalDate date) {
                        sheet.value(r, column, date);
                        sheet.style(r, column).format("yyyy-mm-dd").set();
                    } else if (cell instanceof Number number) {
                        sheet.value(r, column, number);
                    } else if (cell != null) {
                        sheet.value(r, column, cell.toString());
                    }
                }
                r++;
            }
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Sheet names hold at most 31 characters and none of {@code []:*?/\}. */
    private static String sheetName(String title) {
        String name = title.replaceAll("[\\[\\]:*?/\\\\]", " ");
        return name.length() <= MAX_SHEET_NAME ? name : name.substring(0, MAX_SHEET_NAME);
    }
}
