package com.lawdesk.library;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The read-only door other modules use to reach the judgments library (LD-059: the legal assistant). Judgments are found by
 * {@link LibraryJudgmentService#search} and, for a case, by {@link SimilarJudgmentService#forCase} - the same words, the same
 * fixed scoring, the same masking of a restricted judgment's parties; the assistant adds no ranking of its own. Only active
 * judgments are returned, with what is needed to cite them (court, number, year, date, source) and what the library itself
 * has recorded about them (direction, finality, warnings): the library records those facts, nobody here decides them. Of the
 * principles only those a member has REVIEWED are offered; a draft principle is not yet the library's.
 */
@Component
public class LibraryReadPort {

    /** A judgment of the library as it is recorded; {@code reasons} says why a case-based suggestion was offered. */
    public record Authority(UUID id, String courtName, String courtLevel, String caseNumber, Integer caseYear,
            LocalDate judgmentDate, String subject, String summary, String direction, String finality,
            String sourceReference, List<String> principles, List<String> warnings, List<String> reasons) {
    }

    private final LibraryJudgmentService judgments;
    private final SimilarJudgmentService similar;
    private final CurrentActor currentActor;

    LibraryReadPort(LibraryJudgmentService judgments, SimilarJudgmentService similar, CurrentActor currentActor) {
        this.judgments = judgments;
        this.similar = similar;
        this.currentActor = currentActor;
    }

    /** True when the signed-in user may open the library (LIBRARY_VIEW); asking first avoids a refusal inside a transaction. */
    public boolean available() {
        return currentActor.has(Permission.LIBRARY_VIEW);
    }

    private void requireLibrary() {
        if (!currentActor.has(Permission.LIBRARY_VIEW)) {
            throw new AccessDeniedException("missing LIBRARY_VIEW");
        }
    }

    /** Active judgments in which the words occur (a spelling-only resemblance is not a match); empty without LIBRARY_VIEW. */
    @Transactional(readOnly = true)
    public List<Authority> search(String words, int limit) {
        if (!currentActor.has(Permission.LIBRARY_VIEW) || words == null || words.isBlank()) {
            return List.of();
        }
        var filters = new LibraryJudgmentService.Filters(words, null, null, null, null, null, null, List.of(), false);
        List<Authority> found = new ArrayList<>();
        for (LibraryViews.ListItem item : judgments.search(filters, PageRequest.of(0, limit)).getContent()) {
            if (item.matchedIn().stream().allMatch("SIMILAR_TEXT"::equals)) {
                continue;
            }
            found.add(authority(judgments.get(item.id()), List.of()));
        }
        return found;
    }

    /**
     * The judgments the library's fixed comparison offers for a case the user may see (404 otherwise).
     *
     * @throws AccessDeniedException without LIBRARY_VIEW (403)
     */
    @Transactional(readOnly = true)
    public List<Authority> similarTo(UUID caseId, int limit) {
        requireLibrary();
        List<Authority> found = new ArrayList<>();
        for (LibraryViews.Suggestion s : similar.forCase(caseId, limit).items()) {
            List<String> reasons = s.reasons().stream().map(r -> r.code() + (r.terms().isEmpty() ? "" : ": " + String.join(" ", r.terms())))
                    .toList();
            found.add(authority(judgments.get(s.judgment().id()), reasons));
        }
        return found;
    }

    private static Authority authority(LibraryViews.Judgment j, List<String> reasons) {
        return new Authority(j.id(), j.courtName(), j.courtLevel(), j.caseNumber(), j.caseYear(), j.judgmentDate(), j.subject(),
                j.summary(), j.direction(), j.finality(), j.sourceReference(),
                j.principles().stream().filter(p -> "REVIEWED".equals(p.reviewStatus())).map(LibraryViews.Principle::principleText)
                        .toList(), j.validity().warnings(), reasons);
    }
}
