package com.lawdesk.proceedings;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request and response bodies of the judgment API (LD-022, extended by LD-051). {@code final} is a Java keyword, hence
 * the renames. The legacy fields (date, outcome, summary, final) are unchanged; the extended fields travel in
 * {@code details}: a request without it leaves them as they are, a request with it replaces all of them.
 */
final class JudgmentRequests {

    private JudgmentRequests() {
    }

    /** The extended judgment (requirements section 9). Every field is optional; the amounts are KWD with 3 decimals. */
    record Details(
            @Size(max = 100) String chamber,
            UUID judgmentTypeId,
            UUID natureId,
            @Size(max = 100000) String operativeText,
            @Size(max = 100000) String reasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal costsAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal lawyerFeesAmount,
            Boolean appealable,
            LocalDate notifiedOn,
            JudgmentStatus status,
            UUID copyDocumentId) {

        Details {
            chamber = blankToNull(chamber);
            operativeText = blankToNull(operativeText);
            reasons = blankToNull(reasons);
        }

        static final Details EMPTY = new Details(null, null, null, null, null, null, null, null, null, null, null);

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    /** A missing {@code final} means not final. */
    record Create(
            @NotNull @PastOrPresent LocalDate judgmentDate,
            @NotNull Outcome outcome,
            @Size(max = 4000) String summary,
            @JsonProperty("final") Boolean finalJudgment,
            @Valid Details details) {
    }

    record Update(
            @NotNull @PastOrPresent LocalDate judgmentDate,
            @NotNull Outcome outcome,
            @Size(max = 4000) String summary,
            @JsonProperty("final") Boolean finalJudgment,
            @Valid Details details,
            @NotNull Long version) {

        Create decision() {
            return new Create(judgmentDate, outcome, summary, finalJudgment, details);
        }
    }

    /** The extended fields as answered; the lookup labels are those of the chosen values. */
    record DetailsView(String chamber, UUID judgmentTypeId, String judgmentTypeLabelAr, String judgmentTypeLabelEn,
            UUID natureId, String natureLabelAr, String natureLabelEn, String operativeText, String reasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costsAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lawyerFeesAmount, Boolean appealable,
            LocalDate notifiedOn, JudgmentStatus status, UUID copyDocumentId) {
    }

    /** The deadline of the register that the judgment names as its appeal deadline; its period is never stated by the system. */
    record AppealDeadlineView(UUID id, LocalDate dueOn, String status, String calcStatus, String action) {
    }

    /** Names (or, with a null id, clears) the judgment's appeal deadline; the deadline must run from this judgment. */
    record AppealDeadlineBody(UUID deadlineId) {
    }

    record Response(UUID id, UUID proceedingId, UUID caseId, LocalDate judgmentDate, Outcome outcome, String summary,
            @JsonProperty("final") boolean finalJudgment, DetailsView details, AppealDeadlineView appealDeadline,
            long version, Instant createdAt, Instant updatedAt) {

        static Response of(Judgment j, UUID caseId, DetailsView details, AppealDeadlineView appealDeadline) {
            return new Response(j.getId(), j.getProceedingId(), caseId, j.getJudgmentDate(), j.getOutcome(),
                    j.getSummary(), j.isFinal(), details, appealDeadline, j.getVersion(), j.getCreatedAt(),
                    j.getUpdatedAt());
        }
    }
}
