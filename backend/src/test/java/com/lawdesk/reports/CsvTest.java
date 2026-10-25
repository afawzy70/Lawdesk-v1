package com.lawdesk.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LD-034 criterion 2: UTF-8 with a BOM, CRLF, RFC 4180 quoting and formula-injection neutralisation. */
class CsvTest {

    private static String text(byte[] bytes) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void startsWithAUtf8ByteOrderMarkAndEndsEveryLineWithCrlf() {
        byte[] csv = Csv.write(List.of("Reference", "Title"), List.of(List.of("A-1", "Nile"), List.of("B-2", "قضية")));
        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(text(csv)).isEqualTo("Reference,Title\r\nA-1,Nile\r\nB-2,قضية\r\n");
    }

    @Test
    void anEmptyReportIsTheHeaderOnly() {
        assertThat(text(Csv.write(List.of("A", "B"), List.of()))).isEqualTo("A,B\r\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "a,b | `\"a,b\"`",
            "say \"hi\" | `\"say \"\"hi\"\"\"`",
            "plain | plain",
            "' | '"})
    void quotesFieldsThatNeedItAndDoublesTheirQuotes(String value, String expected) {
        assertThat(text(Csv.write(List.of("h"), List.of(List.of(value))))).isEqualTo("h\r\n" + expected + "\r\n");
    }

    @Test
    void quotesALineFeedOrACarriageReturn() {
        assertThat(Csv.cell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(Csv.cell("a\rb")).isEqualTo("\"a\rb\"");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "=SUM(A1:A9) | '=SUM(A1:A9)",
            "+201000000 | '+201000000",
            "-2+3 | '-2+3",
            "@cmd | '@cmd",
            "`\tlead` | `'\tlead`"})
    void neutralisesCellsThatASpreadsheetWouldEvaluate(String value, String expected) {
        assertThat(Csv.cell(value)).isEqualTo(expected);
    }

    @Test
    void neutralisesAndQuotesALeadingCarriageReturnOrAFormulaWithAComma() {
        assertThat(Csv.cell("\r=1")).isEqualTo("\"'\r=1\"");
        assertThat(Csv.cell("=A1,B1")).isEqualTo("\"'=A1,B1\"");
    }

    @Test
    void writesNullAsAnEmptyCellAndLeavesInnerFormulaCharactersAlone() {
        assertThat(Csv.cell(null)).isEmpty();
        assertThat(Csv.cell("a=b")).isEqualTo("a=b");
        assertThat(Csv.cell("2026-03-01")).isEqualTo("2026-03-01");
    }
}
