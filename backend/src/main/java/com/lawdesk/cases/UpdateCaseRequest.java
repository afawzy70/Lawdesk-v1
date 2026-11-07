package com.lawdesk.cases;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/cases/{id}} (LD-013): the editable fields with the creation rules, and the version the client
 * read. The reference number and the status are not editable here. The automated number and the financial impact
 * (LD-045) are optional on an edit: an absent value leaves the stored one as it is; "YES" needs a positive amount of at
 * most three decimals, "NO" takes none and clears the stored one.
 */
record UpdateCaseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Pattern(regexp = CaseType.PATTERN) String caseType,
        @Size(max = 4000) String subject,
        @NotNull @PastOrPresent LocalDate openedOn,
        @NotNull Long version,
        @Size(max = 100) String automatedNo,
        @Pattern(regexp = FinancialImpact.PATTERN) String financialImpact,
        @Positive @Digits(integer = 12, fraction = 3) BigDecimal financialAmount) {

    UpdateCaseRequest {
        title = Text.trimToNull(title);
        subject = Text.trimToNull(subject);
        automatedNo = Text.trimToNull(automatedNo);
    }
}
