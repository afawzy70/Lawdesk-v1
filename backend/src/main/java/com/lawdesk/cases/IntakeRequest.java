package com.lawdesk.cases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/cases/intake} (LD-045): a new case as the government intake registers it, in one request and one
 * transaction. Both case numbers, the authority's position, the first-instance proceeding, the specialist member and the
 * financial-impact choice are required. Text is trimmed; free text carries no format rule beyond a technical length.
 */
record IntakeRequest(
        /** الرقم الآلي للقضية: typed by the user, kept as text. */
        @NotBlank @Size(max = 100) String automatedNo,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Pattern(regexp = CaseType.PATTERN) String caseType,
        @Size(max = 4000) String subject,
        @NotNull @PastOrPresent LocalDate openedOn,
        /** The first stage, with its own number (رقم القضية في درجة التقاضي). */
        @NotNull @Valid FirstInstance firstInstance,
        @NotNull @Pattern(regexp = Side.PATTERN) String authorityPosition,
        /** The parties besides the authority, on either side. */
        @Size(max = 50) List<@NotNull @Valid IntakeParty> parties,
        @NotNull UUID primaryMemberId,
        UUID alternateMemberId,
        @NotNull @Pattern(regexp = FinancialImpact.PATTERN) String financialImpact,
        @Positive @Digits(integer = 12, fraction = 3) BigDecimal financialAmount,
        /** Chamber, receipt, referral, facts, claimed amount, importance and notes; all optional. */
        @Valid CaseDetails details) {

    IntakeRequest {
        automatedNo = Text.trimToNull(automatedNo);
        title = Text.trimToNull(title);
        subject = Text.trimToNull(subject);
        parties = parties == null ? List.of() : parties;
        details = details == null ? CaseDetails.EMPTY : details;
    }

    record FirstInstance(
            @NotNull UUID courtId,
            @NotBlank @Size(max = 32) String courtCaseNumber,
            @NotNull @Min(1900) @Max(2100) Integer courtCaseYear,
            @NotNull @PastOrPresent LocalDate filingDate) {

        FirstInstance {
            courtCaseNumber = Text.trimToNull(courtCaseNumber);
        }
    }

    /** A registered party ({@code partyId}) or a new one ({@code newParty}), on a side of the case. */
    record IntakeParty(UUID partyId, @Valid PartyRequest newParty, @NotNull @Pattern(regexp = Side.PATTERN) String side) {
    }
}
