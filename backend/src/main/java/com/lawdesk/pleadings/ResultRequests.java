package com.lawdesk.pleadings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Bodies and views of the results a judgment gives to claims (sections 5 and 9) and positions on defences (17). */
final class ResultRequests {

    private ResultRequests() {
    }

    record ClaimEntry(@NotNull UUID claimId, @NotNull ClaimResult result, @Size(max = 100000) String operative,
            @Size(max = 100000) String reasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal awardedAmount,
            @Size(max = 2000) String notes) {

        ClaimEntry {
            operative = CaseRows.blankToNull(operative);
            reasons = CaseRows.blankToNull(reasons);
            notes = CaseRows.blankToNull(notes);
        }
    }

    record ClaimResults(@NotEmpty @Size(max = 500) List<@Valid ClaimEntry> results) {
    }

    /** One claim of the case with the result this judgment gave it, if any ({@code result} is null before it is recorded). */
    record ClaimResultView(UUID claimId, int claimSeq, String claimText, ClaimKind claimKind, ClaimStatus claimStatus,
            ClaimResult result, String operative, String reasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal awardedAmount, String notes, Long version) {
    }

    /** A result as the case view lists them: with the judgment and the stage it comes from. */
    record CaseClaimResult(UUID judgmentId, UUID proceedingId, String stage, LocalDate judgmentDate, UUID claimId,
            int claimSeq, ClaimResult result, String operative, String reasons,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal awardedAmount, String notes) {
    }

    record DefenseEntry(@NotNull UUID defenseId, @NotNull DefensePosition position, @Size(max = 100000) String reasons,
            @Size(max = 100000) String operative) {

        DefenseEntry {
            reasons = CaseRows.blankToNull(reasons);
            operative = CaseRows.blankToNull(operative);
        }
    }

    record DefensePositions(@NotEmpty @Size(max = 500) List<@Valid DefenseEntry> positions) {
    }

    record DefensePositionView(UUID defenseId, int defenseSeq, String defenseTitle, DefenseKind defenseKind,
            DefensePosition position, String reasons, String operative, Long version) {
    }

    record CaseDefensePosition(UUID judgmentId, UUID proceedingId, String stage, LocalDate judgmentDate, UUID defenseId,
            int defenseSeq, DefensePosition position, String reasons, String operative) {
    }
}
