package com.lawdesk.api;

import java.util.List;

import org.springframework.data.domain.Page;

/** The shared list shape (architecture/CONTRACTS.md §4). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
