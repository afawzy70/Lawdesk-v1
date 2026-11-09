package com.lawdesk.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * The read-only door other modules use to reuse the unified search (LD-059: the legal assistant retrieves through it instead
 * of forking it). It is {@link SearchService} itself: the same Arabic normalisation, the same ranking and, above all, the same
 * case-access rule, so a hit, a snippet or a total can only come from a record the signed-in user may see, and a type the
 * user holds no permission for is not searched at all.
 */
@Component
public class SearchPort {

    /** One hit: the record, the case it belongs to (null for the library) and a piece of its original text. */
    public record Hit(String type, UUID id, UUID caseId, String caseReferenceNo, String title, String detail, LocalDate date,
            String snippet, List<String> matchedIn) {
    }

    private final SearchService service;

    SearchPort(SearchService service) {
        this.service = service;
    }

    /**
     * The records of {@code types} (all searchable types when empty) in which every word of {@code query} occurs, at most
     * {@code limit} per type, optionally inside one case. A blank query finds nothing.
     */
    public List<Hit> find(String query, UUID caseId, List<String> types, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        SearchRequests.Result result = service.search(new SearchRequests.Query(query, types, null, caseId, null, null, 0, limit));
        List<Hit> hits = new ArrayList<>();
        for (SearchRequests.Group group : result.groups()) {
            for (SearchRequests.Item item : group.items()) {
                hits.add(new Hit(item.type(), item.id(), item.caseId(), item.caseReferenceNo(), item.title(), item.detail(),
                        item.date(), item.snippet(), item.matchedIn()));
            }
        }
        return hits;
    }
}
