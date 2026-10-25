package com.lawdesk.reports;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV for report exports (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034 criterion 2; architecture/CONTRACTS.md §8.3):
 * UTF-8 with a byte order mark so Excel reads Arabic, CRLF line ends and RFC 4180 quoting. A cell a spreadsheet would
 * evaluate as a formula (starting with {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return) is
 * prefixed with {@code '}.
 */
final class Csv {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Csv() {
    }

    static byte[] write(List<String> header, List<List<String>> rows) {
        StringBuilder text = new StringBuilder();
        line(text, header);
        for (List<String> row : rows) {
            line(text, row);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length() * 2 + BOM.length);
        out.writeBytes(BOM);
        out.writeBytes(text.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void line(StringBuilder text, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(cell(cells.get(i)));
        }
        text.append("\r\n");
    }

    /** One cell: neutralised when it would be a formula, then quoted when it holds a comma, quote, CR or LF. */
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.indexOf(',') < 0 && safe.indexOf('"') < 0 && safe.indexOf('\r') < 0 && safe.indexOf('\n') < 0) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
