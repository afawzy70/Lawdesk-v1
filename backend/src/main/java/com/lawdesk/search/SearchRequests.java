package com.lawdesk.search;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/** Query parameters and answer of the unified search (LD-057, requirements section 28). */
final class SearchRequests {

    static final int DEFAULT_SIZE = 5;
    static final int MAX_SIZE = 100;

    private SearchRequests() {
    }

    /**
     * Bound as one object, so a malformed value is a field error. {@code types} narrows the record types searched,
     * {@code page} and {@code size} page each group, and the rest are the advanced filters.
     */
    record Query(
            @Size(max = 200) String q,
            List<@Pattern(regexp = "[A-Z_]{1,20}") String> types,
            @Pattern(regexp = "[A-Z_]{1,32}") String caseType,
            UUID caseId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Min(0) Integer page,
            @Min(1) Integer size) {

        Query {
            q = q == null || q.isBlank() ? null : q.strip();
            types = types == null ? List.of() : List.copyOf(types);
        }

        int pageOrDefault() {
            return page == null ? 0 : page;
        }

        int sizeOrDefault() {
            return size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        }
    }

    /**
     * One hit. {@code snippet} is cut from the original text of a record the user may see; {@code matchedIn} names the
     * parts of the record where the words were found; {@code caseId} is empty for records that belong to no case (library).
     */
    record Item(UUID id, String type, UUID caseId, String caseReferenceNo, String title, String detail, LocalDate date,
            String snippet, List<String> matchedIn, double rank, Integer caseCount) {
    }

    record Group(String type, long total, int page, int size, List<Item> items) {
    }

    /** {@code total} counts only what the user may see; {@code searchedTypes} says which types were searched for them. */
    record Result(String query, long total, List<String> searchedTypes, List<Group> groups) {
    }
}
