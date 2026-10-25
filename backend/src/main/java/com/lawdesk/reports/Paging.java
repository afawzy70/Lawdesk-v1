package com.lawdesk.reports;

import java.util.Locale;
import java.util.Map;

/**
 * Paging and sorting of report lists (architecture/CONTRACTS.md §4): {@code page} from 0, {@code size} default 20 and
 * at most 100 (larger values are refused with 400 by PagingValidationInterceptor), {@code sort=<field>,<asc|desc>} whose field the query's pattern already restricted to a whitelist.
 */
record Paging(int page, int size) {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    static Paging of(Integer page, Integer size) {
        return new Paging(page == null ? 0 : page, size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }

    long offset() {
        return (long) page * size;
    }

    int totalPages(long totalItems) {
        return (int) ((totalItems + size - 1) / size);
    }

    /** A validated sort parameter: the field it names and whether it runs descending. */
    record Order<F>(F field, boolean descending) {

        /** {@code sort} as validated by the query pattern, or {@code fallback} when it is absent. */
        static <F> Order<F> parse(String sort, Map<String, F> fields, Order<F> fallback) {
            if (sort == null) {
                return fallback;
            }
            String[] parts = sort.split(",");
            F field = fields.get(parts[0]);
            if (field == null) {
                throw new IllegalArgumentException("unsupported sort field " + parts[0]);
            }
            return new Order<>(field, parts.length > 1 && parts[1].toLowerCase(Locale.ROOT).equals("desc"));
        }
    }
}
