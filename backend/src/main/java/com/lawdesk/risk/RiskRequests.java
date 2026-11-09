package com.lawdesk.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Request and response bodies of the risk-assessment API (LD-053, requirements section 19). */
final class RiskRequests {

    /** The fixed notice of the requirements: the assessment is a decision aid, not a judicial forecast. */
    static final String NOTICE = "The risk assessment is a decision aid. It is not a certain judicial forecast and does"
            + " not replace the legal opinion.";

    private RiskRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    enum Level { LOW, MEDIUM, HIGH, VERY_HIGH;

        boolean high() {
            return this == HIGH || this == VERY_HIGH;
        }
    }

    enum Trigger { NEW_DOCUMENT, JUDGMENT, DECISION, POSITION_CHANGE, PERIODIC, MANUAL }

    /** How much an element adds to the risk; "not assessed" is a value, never a guess. */
    enum Rating { NOT_ASSESSED, LOW, MEDIUM, HIGH, VERY_HIGH }

    /** The ten elements of the assessment in the order of the requirements document. */
    enum ElementCode {
        LEGAL_POSITION_STRENGTH, EVIDENCE_STRENGTH, ADMINISTRATIVE_PROCEDURE_SOUNDNESS, POTENTIAL_PROCEDURAL_DEFECTS,
        SIMILAR_PRECEDENTS, JUDICIAL_TREND_STABILITY, POTENTIAL_FINANCIAL_LOSS, ADMINISTRATIVE_IMPACT,
        SPILLOVER_TO_OTHER_CASES, INFORMATION_AVAILABILITY
    }

    enum RemediationStatus { OPEN, IN_PROGRESS, DONE, CANCELLED }

    record ElementInput(@NotNull ElementCode code, @NotNull Rating rating, @Size(max = 2000) String reason) {

        ElementInput {
            reason = trim(reason);
        }
    }

    record RemediationInput(
            @NotBlank @Size(max = 1000) String cause,
            @NotBlank @Size(max = 1000) String action,
            @Size(max = 1000) String documents,
            @Size(max = 1000) String defenses,
            @Size(max = 300) String unitToAddress,
            UUID ownerId,
            LocalDate targetDate) {

        RemediationInput {
            cause = trim(cause);
            action = trim(action);
            documents = trim(documents);
            defenses = trim(defenses);
            unitToAddress = trim(unitToAddress);
        }
    }

    record RemediationUpdate(
            @NotBlank @Size(max = 1000) String cause,
            @NotBlank @Size(max = 1000) String action,
            @Size(max = 1000) String documents,
            @Size(max = 1000) String defenses,
            @Size(max = 300) String unitToAddress,
            UUID ownerId,
            LocalDate targetDate,
            @NotNull RemediationStatus status,
            @NotNull Long version) {

        RemediationUpdate {
            cause = trim(cause);
            action = trim(action);
            documents = trim(documents);
            defenses = trim(defenses);
            unitToAddress = trim(unitToAddress);
        }
    }

    /** A new version of the assessment. Elements not listed are recorded as "not assessed". */
    record Assess(
            @NotNull Level level,
            @NotBlank @Size(max = 4000) String levelReasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal potentialLoss,
            @NotNull Trigger trigger,
            @Size(max = 500) String triggerNote,
            UUID triggerRefId,
            @Valid List<ElementInput> elements,
            @Valid List<RemediationInput> remediation) {

        Assess {
            levelReasons = trim(levelReasons);
            triggerNote = trim(triggerNote);
            elements = elements == null ? List.of() : elements;
            remediation = remediation == null ? List.of() : remediation;
        }
    }

    record Element(String code, String rating, String reason) {
    }

    record Remediation(UUID id, UUID assessmentId, UUID caseId, String cause, String action, String documents,
            String defenses, String unitToAddress, UUID ownerId, String ownerName, LocalDate targetDate, String status,
            long version, Instant createdAt, Instant updatedAt) {
    }

    record Assessment(UUID id, UUID caseId, int versionNo, String level, String levelReasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal potentialLoss, String trigger, String triggerNote,
            UUID triggerRefId, UUID assessedBy, String assessedByName, Instant assessedAt, List<Element> elements,
            List<Remediation> remediation) {
    }

    record HighRisk(UUID caseId, String caseReferenceNo, String caseTitle, UUID assessmentId, int versionNo, String level,
            String levelReasons, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal potentialLoss, Instant assessedAt,
            long openRemediation, long totalRemediation) {
    }

    record HighRiskReport(String notice, List<HighRisk> items, int page, int size, long totalItems, int totalPages) {
    }
}
