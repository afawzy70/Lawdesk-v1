package com.lawdesk.settlements;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

/** Request and response bodies of the settlements API (LD-057, requirements section 23). */
final class SettlementRequests {

    private SettlementRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** ASSUMPTION (needs the owner's approval): the document names no states for a settlement (docs/design LD-DOC-05, 9). */
    enum Status { PROPOSED, UNDER_REVIEW, APPROVED, REJECTED, EXECUTED, WITHDRAWN }

    /**
     * What a settlement records. The amount is the settlement's own, in Kuwaiti dinars with at most three decimals; it is
     * never combined with any other amount of the case (owner decision D5).
     */
    record Create(
            @NotBlank @Size(max = 40) String kind,
            UUID offeringPartyId,
            @Size(max = 300) String offeringPartyText,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal settlementAmount,
            @Size(max = 20000) String legalGrounds,
            @Size(max = 20000) String financialImpactNote,
            @Size(max = 20000) String legalOpinion,
            @Size(max = 300) String approvingAuthority) {

        Create {
            kind = trim(kind);
            offeringPartyText = trim(offeringPartyText);
            legalGrounds = trim(legalGrounds);
            financialImpactNote = trim(financialImpactNote);
            legalOpinion = trim(legalOpinion);
            approvingAuthority = trim(approvingAuthority);
        }
    }

    record Update(
            @NotBlank @Size(max = 40) String kind,
            UUID offeringPartyId,
            @Size(max = 300) String offeringPartyText,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal settlementAmount,
            @Size(max = 20000) String legalGrounds,
            @Size(max = 20000) String financialImpactNote,
            @Size(max = 20000) String legalOpinion,
            @Size(max = 300) String approvingAuthority,
            @NotNull Long version) {

        Update {
            kind = trim(kind);
            offeringPartyText = trim(offeringPartyText);
            legalGrounds = trim(legalGrounds);
            financialImpactNote = trim(financialImpactNote);
            legalOpinion = trim(legalOpinion);
            approvingAuthority = trim(approvingAuthority);
        }
    }

    /** A move to another state; the approving authority's reason is {@code reason}, the completion date {@code completedOn}. */
    record StatusChange(
            @NotNull Status status,
            @Size(max = 20000) String reason,
            LocalDate completedOn,
            @NotNull Long version) {

        StatusChange {
            reason = trim(reason);
        }
    }

    record DocumentRef(UUID id, String title, String fileName) {
    }

    record Settlement(UUID id, UUID caseId, String kind, String status, UUID offeringPartyId, String offeringPartyName,
            String offeringPartyText,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal settlementAmount, String legalGrounds,
            String financialImpactNote, String legalOpinion, String approvingAuthority, String decisionReason,
            UUID decidedBy, Instant decidedAt, LocalDate completedOn, List<DocumentRef> documents, long version,
            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
    }

    /** One line of the history: {@code changes} is {@code {field: {from, to}}} for an edit. */
    record HistoryEntry(UUID id, String action, String fromStatus, String toStatus, JsonNode changes, String note,
            UUID actorId, String actorName, Instant occurredAt) {
    }
}
