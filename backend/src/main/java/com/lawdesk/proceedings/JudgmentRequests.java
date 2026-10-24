package com.lawdesk.proceedings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request and response bodies of the judgment API (LD-022). {@code final} is a Java keyword, hence the renames. */
final class JudgmentRequests {

    private JudgmentRequests() {
    }

    /** A missing {@code final} means not final. */
    record Create(
            @NotNull @PastOrPresent LocalDate judgmentDate,
            @NotNull Outcome outcome,
            @Size(max = 4000) String summary,
            @JsonProperty("final") Boolean finalJudgment) {
    }

    record Update(
            @NotNull @PastOrPresent LocalDate judgmentDate,
            @NotNull Outcome outcome,
            @Size(max = 4000) String summary,
            @JsonProperty("final") Boolean finalJudgment,
            @NotNull Long version) {

        Create decision() {
            return new Create(judgmentDate, outcome, summary, finalJudgment);
        }
    }

    record Response(UUID id, UUID proceedingId, UUID caseId, LocalDate judgmentDate, Outcome outcome, String summary,
            @JsonProperty("final") boolean finalJudgment, long version, Instant createdAt, Instant updatedAt) {

        static Response of(Judgment j, UUID caseId) {
            return new Response(j.getId(), j.getProceedingId(), caseId, j.getJudgmentDate(), j.getOutcome(),
                    j.getSummary(), j.isFinal(), j.getVersion(), j.getCreatedAt(), j.getUpdatedAt());
        }
    }
}
