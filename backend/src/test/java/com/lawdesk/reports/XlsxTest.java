package com.lawdesk.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * QAD-035: the sheet declares its real dimension, so streaming readers (openpyxl {@code read_only=True} sizes its rows
 * from {@code <dimension>}) see every row. The sheet is read here the way such a reader does, with a streaming XML
 * parser, never with the writer's own model.
 */
class XlsxTest {

    /** What a streaming reader learns from the sheet part: the declared dimension and the cells actually present. */
    record Sheet(String dimension, int rows, String lastCell) {
    }

    static Sheet read(byte[] xlsx) throws IOException, XMLStreamException {
        Path file = Files.createTempFile("xlsx-test", ".xlsx");
        try {
            Files.write(file, xlsx);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry entry = zip.getEntry("xl/worksheets/sheet1.xml");
                assertThat(entry).as("sheet1.xml").isNotNull();
                try (InputStream in = zip.getInputStream(entry)) {
                    return stream(in);
                }
            }
        } finally {
            Files.delete(file);
        }
    }

    private static Sheet stream(InputStream in) throws XMLStreamException {
        XMLStreamReader xml = XMLInputFactory.newFactory().createXMLStreamReader(in);
        String dimension = null;
        int rows = 0;
        int lastRow = 0;
        int lastColumn = 0;
        while (xml.hasNext()) {
            if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (xml.getLocalName()) {
                case "dimension" -> dimension = xml.getAttributeValue(null, "ref");
                case "row" -> rows++;
                case "c" -> {
                    String ref = xml.getAttributeValue(null, "r");
                    int split = 0;
                    while (Character.isLetter(ref.charAt(split))) {
                        split++;
                    }
                    lastColumn = Math.max(lastColumn, column(ref.substring(0, split)));
                    lastRow = Math.max(lastRow, Integer.parseInt(ref.substring(split)));
                }
                default -> {
                }
            }
        }
        return new Sheet(dimension, rows, letters(lastColumn) + lastRow);
    }

    private static int column(String letters) {
        int column = 0;
        for (char c : letters.toCharArray()) {
            column = column * 26 + (c - 'A' + 1);
        }
        return column;
    }

    private static String letters(int column) {
        StringBuilder text = new StringBuilder();
        for (int n = column; n > 0; n = (n - 1) / 26) {
            text.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return text.toString();
    }

    private static ExportTable table(ReportLabels labels, int rows, int columns) {
        List<String> header = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            header.add("H" + c);
        }
        List<List<Object>> body = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            List<Object> row = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                row.add(c == 1 ? LocalDate.of(2026, 3, 1) : c == 2 ? Long.valueOf(r) : "v" + r + "-" + c);
            }
            body.add(row);
        }
        return new ExportTable("Title", "2026-10-24", labels, header, body);
    }

    @ParameterizedTest
    @EnumSource(ReportLabels.class)
    void declaresTheDimensionOfTheCellsItHolds(ReportLabels labels) throws Exception {
        Sheet sheet = read(Xlsx.write(table(labels, 6, 9)));
        assertThat(sheet.rows()).isEqualTo(7);
        assertThat(sheet.lastCell()).isEqualTo("I7");
        assertThat(sheet.dimension()).isEqualTo("A1:I7");
    }

    @Test
    void declaresTheDimensionAtTheTenThousandRowLimit() throws Exception {
        Sheet sheet = read(Xlsx.write(table(ReportLabels.AR, ReportExports.MAX_ROWS, 9)));
        assertThat(sheet.rows()).isEqualTo(10_001);
        assertThat(sheet.dimension()).isEqualTo("A1:I10001");
    }

    @Test
    void declaresAHeaderOnlySheetAndASingleColumn() throws Exception {
        assertThat(read(Xlsx.write(table(ReportLabels.EN, 0, 4))).dimension()).isEqualTo("A1:D1");
        assertThat(read(Xlsx.write(table(ReportLabels.EN, 3, 1))).dimension()).isEqualTo("A1:A4");
    }

    @Test
    void namesColumnsPastZ() throws Exception {
        Sheet sheet = read(Xlsx.write(table(ReportLabels.EN, 2, 28)));
        assertThat(sheet.lastCell()).isEqualTo("AB3");
        assertThat(sheet.dimension()).isEqualTo("A1:AB3");
    }

    @Test
    void keepsEveryOtherPartOfTheWorkbook() throws Exception {
        byte[] xlsx = Xlsx.write(table(ReportLabels.AR, 2, 3));
        Path file = Files.createTempFile("xlsx-test", ".xlsx");
        try {
            Files.write(file, xlsx);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                List<String> names = zip.stream().map(ZipEntry::getName).toList();
                assertThat(names).contains("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml",
                        "xl/styles.xml");
                try (InputStream in = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))) {
                    String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(xml).contains("rightToLeft=\"true\"").contains("<pane").contains("<cols>");
                    assertThat(Arrays.stream(xml.split("<dimension")).count()).isEqualTo(2);
                }
            }
        } finally {
            Files.delete(file);
        }
    }
}
