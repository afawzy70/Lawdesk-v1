package com.lawdesk.assistant;

import java.util.List;
import java.util.UUID;

import com.lawdesk.assistant.AssistantProvider.Section;
import com.lawdesk.assistant.AssistantProvider.Source;

/**
 * What retrieval found for one request: the numbered passages and how the answer groups them. When {@code refusal} is set the
 * retrieval found nothing adequate to answer from, and the assistant says so instead of answering.
 */
record Plan(UUID caseId, List<Source> sources, List<Section> sections, String refusal) {

    static final String NO_KEYWORDS = "NO_SEARCH_WORDS";
    static final String NO_RELEVANT_SOURCES = "NO_RELEVANT_SOURCES";
    static final String DOCUMENT_TEXT_UNAVAILABLE = "DOCUMENT_TEXT_UNAVAILABLE";

    static Plan refused(UUID caseId, String reason) {
        return new Plan(caseId, List.of(), List.of(), reason);
    }

    static Plan of(UUID caseId, SourceBook book, List<Section> sections) {
        boolean anything = sections.stream().anyMatch(s -> !s.sources().isEmpty());
        return anything ? new Plan(caseId, book.sources(), List.copyOf(sections), null) : refused(caseId, NO_RELEVANT_SOURCES);
    }
}
