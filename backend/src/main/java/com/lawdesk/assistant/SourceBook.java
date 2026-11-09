package com.lawdesk.assistant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lawdesk.assistant.AssistantProvider.Field;
import com.lawdesk.assistant.AssistantProvider.Source;

/** The numbered passages one answer rests on: a passage is added once however many sections quote it. */
final class SourceBook {

    static final int EXCERPT_MAX = 700;

    private final List<Source> sources = new ArrayList<>();
    private final Map<String, Integer> numbers = new HashMap<>();

    /** Adds the passage unless the same record is already there; returns its number. */
    int add(String type, UUID id, UUID caseId, String caseReferenceNo, String title, LocalDate date, String excerpt,
            List<Field> fields) {
        String clipped = clip(excerpt);
        String key = type + "|" + (id == null ? "-" : id) + "|" + (id == null || type.equals("TIMELINE") || type.equals("DOCUMENT") ? title + "|" + clipped + "|" + fields : "");
        Integer existing = numbers.get(key);
        if (existing != null) {
            return existing;
        }
        int n = sources.size() + 1;
        StringBuilder all = new StringBuilder(clipped == null ? "" : clipped);
        if (fields != null) {
            fields.forEach(f -> all.append(' ').append(f.value()));
        }
        sources.add(new Source(n, type, id, caseId, caseReferenceNo, title, date, null, clipped, fields == null ? List.of() : List.copyOf(fields),
                InstructionMarkers.looksLikeInstruction(all.toString())));
        numbers.put(key, n);
        return n;
    }

    List<Source> sources() {
        return List.copyOf(sources);
    }

    boolean isEmpty() {
        return sources.isEmpty();
    }

    static String clip(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= EXCERPT_MAX ? flat : flat.substring(0, EXCERPT_MAX - 1) + "…";
    }
}
