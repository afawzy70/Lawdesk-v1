package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;

/**
 * An export before it is written in a format (LD-034): its title (the report's name), subtitle (the export date or
 * the period), column names and rows in the export language. Cells are text, calendar dates or whole numbers, or null
 * when empty; each format writes them its own way.
 */
record ExportTable(String title, String subtitle, ReportLabels labels, List<String> header,
        List<List<Object>> rows) {

    /** A cell as text: dates as ISO {@code yyyy-MM-dd}, numbers as plain digits, null as empty. */
    static String text(Object cell) {
        if (cell == null) {
            return "";
        }
        if (cell instanceof LocalDate date) {
            return date.toString();
        }
        if (cell instanceof java.math.BigDecimal money) {
            // Money is KWD with three decimals, always (decision D5).
            return money.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        return cell.toString();
    }

    List<List<String>> textRows() {
        return rows.stream().map(row -> row.stream().map(ExportTable::text).toList()).toList();
    }
}
