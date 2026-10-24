package com.lawdesk.proceedings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the special decision API (LD-023). */
final class SpecialDecisionRequests {

    private SpecialDecisionRequests() {
    }

    /** {@code judgmentId} is required for an enforcement suspension (422) and optional otherwise. */
    record Create(
            @NotNull DecisionType decisionType,
            @NotNull @PastOrPresent LocalDate decisionDate,
            @NotNull DecisionEffect effect,
            @Size(max = 4000) String summary,
            UUID judgmentId) {
    }

    record Update(
            @NotNull DecisionType decisionType,
            @NotNull @PastOrPresent LocalDate decisionDate,
            @NotNull DecisionEffect effect,
            @Size(max = 4000) String summary,
            UUID judgmentId,
            @NotNull Long version) {

        Create decision() {
            return new Create(decisionType, decisionDate, effect, summary, judgmentId);
        }
    }

    record Response(UUID id, UUID proceedingId, UUID caseId, DecisionType decisionType, LocalDate decisionDate,
            DecisionEffect effect, String summary, UUID judgmentId, long version, Instant createdAt,
            Instant updatedAt) {

        static Response of(SpecialDecision d, UUID caseId) {
            return new Response(d.getId(), d.getProceedingId(), caseId, d.getDecisionType(), d.getDecisionDate(),
                    d.getEffect(), d.getSummary(), d.getJudgmentId(), d.getVersion(), d.getCreatedAt(),
                    d.getUpdatedAt());
        }
    }
}
