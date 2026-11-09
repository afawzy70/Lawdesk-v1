package com.lawdesk.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The answer of a report family: titled columns and typed rows. Labels are in the language asked for. In JSON, money is a
 * string with three decimals (KWD, decision D5) and counts are numbers; exports keep the typed values (Excel gets
 * numbers and dates). Amounts of different kinds are always separate columns and are never added to each other.
 */
public record ReportTable(String family, String title, boolean available, String unavailableReason,
        List<String> groupings, String groupBy, List<Column> columns, List<List<Object>> rows, boolean hasTotal, String note) {

    /** A column: its key, its label and its kind (TEXT, COUNT, MONEY, DECIMAL, DATE). */
    public record Column(String key, String label, String type) {
    }

    /** The cells as JSON shows them: money as a three-decimal string, dates as ISO text. */
    public List<List<Object>> rowsForJson() {
        List<List<Object>> out = new ArrayList<>();
        for (List<Object> row : rows) {
            List<Object> cells = new ArrayList<>();
            for (Object cell : row) {
                if (cell instanceof BigDecimal money) {
                    cells.add(money.setScale(3, RoundingMode.HALF_UP).toPlainString());
                } else if (cell instanceof LocalDate date) {
                    cells.add(date.toString());
                } else {
                    cells.add(cell);
                }
            }
            out.add(cells);
        }
        return out;
    }

    /** The JSON form (the record itself holds typed cells). */
    public Json json() {
        return new Json(family, title, available, unavailableReason, groupings, groupBy, columns, rowsForJson(), hasTotal, note);
    }

    /** What the API sends. */
    public record Json(String family, String title, boolean available, String unavailableReason, List<String> groupings,
            String groupBy, List<Column> columns, List<List<Object>> rows, boolean hasTotal, String note) {
    }
}
