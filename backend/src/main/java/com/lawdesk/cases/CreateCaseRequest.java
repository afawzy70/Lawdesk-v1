package com.lawdesk.cases;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/cases} (LD-012). Text is trimmed before validation; a blank optional value counts as absent, and
 * an absent reference number is generated.
 */
record CreateCaseRequest(
        @Size(max = 40) @Pattern(regexp = Text.REFERENCE_NO) String referenceNo,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Pattern(regexp = CaseType.PATTERN) String caseType,
        @Size(max = 4000) String subject,
        @NotNull @PastOrPresent LocalDate openedOn) {

    CreateCaseRequest {
        referenceNo = Text.trimToNull(referenceNo);
        title = Text.trimToNull(title);
        subject = Text.trimToNull(subject);
    }
}
