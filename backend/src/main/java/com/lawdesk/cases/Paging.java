package com.lawdesk.cases;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.lawdesk.api.PageResponse;

/**
 * Query parameters of the simple lists (timeline, parties): an optional search text and the shared paging rules
 * (architecture/CONTRACTS.md §4). Sizes above the maximum are capped rather than refused.
 */
record Paging(@Size(max = 100) String q, @Min(0) Integer page, @Min(1) Integer size) {

    Paging {
        q = Text.trimToNull(q);
    }

    int pageOrDefault() {
        return page == null ? 0 : page;
    }

    int sizeOrDefault() {
        return size == null ? CaseSearch.DEFAULT_SIZE : Math.min(size, CaseSearch.MAX_SIZE);
    }

    long offset() {
        return (long) pageOrDefault() * sizeOrDefault();
    }

    <T> PageResponse<T> page(List<T> items, long total) {
        int pageSize = sizeOrDefault();
        return new PageResponse<>(items, pageOrDefault(), pageSize, total, (int) ((total + pageSize - 1) / pageSize));
    }
}
