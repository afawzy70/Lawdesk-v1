package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseType;

/**
 * The filters every report family accepts (requirements section 27). They narrow the <em>cases</em> a report counts:
 * the opening period ({@code from}, {@code to}), the dispute type, open or closed, and the primary member. The
 * performance family reads {@code from} and {@code to} as the activity period instead. {@code groupBy} picks one of the
 * family's groupings; {@code lang} (en or ar) is the language of the labels the answer carries.
 */
public record FamilyQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        List<CaseType> caseType,
        StatusGroup statusGroup,
        UUID memberId,
        @Pattern(regexp = "[A-Z_]{1,24}") @Size(max = 24) String groupBy,
        @Pattern(regexp = "en|ar") String lang) {

    public FamilyQuery {
        caseType = caseType == null ? List.of() : List.copyOf(caseType);
    }
}
