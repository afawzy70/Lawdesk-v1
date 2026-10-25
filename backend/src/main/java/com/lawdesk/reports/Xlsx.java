package com.lawdesk.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Excel ({@code .xlsx}) for report exports (LD-034), written with fastexcel. Text is stored as text cells, which a
 * spreadsheet never evaluates as formulas; dates are date cells, counts numbers. An Arabic export is a right-to-left
 * sheet.
 *
 * <p>QAD-035: fastexcel writes {@code <dimension ref="A1"/>} whatever the sheet holds, and streaming readers (openpyxl
 * {@code read_only=True}) size the sheet from it, so they saw one row. The sheet part is rewritten with the real
 * dimension, {@code A1:<last column><last row>}, while the workbook is zipped again.
 */
final class Xlsx {

    private static final int MAX_SHEET_NAME = 31;
    private static final String SHEET_PART = "xl/worksheets/sheet1.xml";
    private static final String WRITER_DIMENSION = "<dimension ref=\"A1\"/>";

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
        int lastRow = table.rows().size() + 1;
        return withDimension(out.toByteArray(), "A1:" + columnName(table.header().size()) + lastRow);
    }

    /** The workbook with the sheet's declared dimension replaced by {@code ref}; every other part is copied as is. */
    static byte[] withDimension(byte[] xlsx, String ref) {
        Path file = null;
        try {
            file = Files.createTempFile("lawdesk-export", ".xlsx");
            Files.write(file, xlsx);
            ByteArrayOutputStream out = new ByteArrayOutputStream(xlsx.length + 64);
            boolean replaced = false;
            try (ZipFile zip = new ZipFile(file.toFile()); ZipOutputStream copy = new ZipOutputStream(out)) {
                for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                    byte[] content;
                    try (InputStream in = zip.getInputStream(entry)) {
                        content = in.readAllBytes();
                    }
                    if (SHEET_PART.equals(entry.getName())) {
                        String xml = new String(content, StandardCharsets.UTF_8);
                        if (!xml.contains(WRITER_DIMENSION)) {
                            throw new IllegalStateException("the sheet does not declare the expected dimension");
                        }
                        content = xml.replace(WRITER_DIMENSION, "<dimension ref=\"" + ref + "\"/>")
                                .getBytes(StandardCharsets.UTF_8);
                        replaced = true;
                    }
                    copy.putNextEntry(new ZipEntry(entry.getName()));
                    copy.write(content);
                    copy.closeEntry();
                }
            }
            if (!replaced) {
                throw new IllegalStateException("the workbook has no sheet part");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // a leftover temporary file is not worth failing an export for
                }
            }
        }
    }

    /** The spreadsheet name of a 1-based column: A…Z, AA…AZ, … */
    static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        for (int n = column; n > 0; n = (n - 1) / 26) {
            name.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return name.toString();
    }

    /** Sheet names hold at most 31 characters and none of {@code []:*?/\}. */
    private static String sheetName(String title) {
        String name = title.replaceAll("[\\[\\]:*?/\\\\]", " ");
        return name.length() <= MAX_SHEET_NAME ? name : name.substring(0, MAX_SHEET_NAME);
    }
}
