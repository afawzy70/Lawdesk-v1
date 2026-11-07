package com.lawdesk.cases;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A stored legal case as the API returns it (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012 criterion 1). */
public record LegalCase(
        UUID id,
        String referenceNo,
        String title,
        CaseType caseType,
        String subject,
        LocalDate openedOn,
        CaseStatus status,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        /** الرقم الآلي للقضية: typed by the user, the same on every stage; null for cases that predate it. */
        String automatedNo,
        /** موقف الهيئة; null for cases that predate it. */
        Side authorityPosition,
        /** الأثر المالي; null means "not specified" (older cases), never "no impact". */
        FinancialImpact financialImpact,
        /** Dinars with three decimals, as text so it is never rounded by a client; null unless there is an impact. */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal financialAmount,
        MemberRef primaryMember,
        MemberRef alternateMember) {

    CaseSummary summary() {
        return new CaseSummary(id, referenceNo, title, status.name());
    }
}
