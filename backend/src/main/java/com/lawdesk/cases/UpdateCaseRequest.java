package com.lawdesk.cases;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/cases/{id}} (LD-013): the editable fields with the creation rules, and the version the client
 * read. The reference number and the status are not editable here.
 */
record UpdateCaseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Pattern(regexp = CaseType.PATTERN) String caseType,
        @Size(max = 4000) String subject,
        @NotNull @PastOrPresent LocalDate openedOn,
        @NotNull Long version) {

    UpdateCaseRequest {
        title = Text.trimToNull(title);
        subject = Text.trimToNull(subject);
    }
}
