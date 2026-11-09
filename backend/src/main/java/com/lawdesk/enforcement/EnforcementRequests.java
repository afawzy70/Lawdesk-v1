package com.lawdesk.enforcement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Requests and answers of judgment enforcement (LD-055, requirements section 11). Money is a KWD string, three decimals. */
final class EnforcementRequests {

    private EnforcementRequests() {
    }

    /** The enforcement file of a judgment; {@code version} is required when changing an existing file. */
    record FileBody(String status, UUID responsibleMemberId, String executingParty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountRequired, String obstacles, LocalDate dueOn,
            LocalDate completedOn, String notes, Long version) {
    }

    record ActionBody(String kind, LocalDate actionDate, String description, String result, UUID documentId, Long version) {
    }

    /** What the enforcement file shows about the judgment itself; the judgment is never copied. */
    record JudgmentRef(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String stage,
            String courtCaseNumber, Integer courtCaseYear, LocalDate judgmentDate, String operativeText, String summary,
            boolean finalJudgment) {
    }

    record FileView(UUID id, UUID judgmentId, UUID caseId, String status, UUID responsibleMemberId,
            String responsibleMemberName, String executingParty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountRequired, String obstacles, LocalDate dueOn,
            LocalDate completedOn, String notes, boolean late, long version, Instant updatedAt) {
    }

    record ActionView(UUID id, UUID enforcementId, int seq, String kind, LocalDate actionDate, String description,
            String result, UUID documentId, String documentTitle, long version, Instant createdAt, UUID createdBy) {
    }

    /** The judgment, its file (null when none is recorded yet) and the actions taken. */
    record Overview(JudgmentRef judgment, FileView file, List<ActionView> actions) {
    }

    /** One judgment in the list of judgments not executed or late; {@code status} is null when no file was recorded. */
    record ListItem(UUID judgmentId, UUID caseId, String caseReferenceNo, String caseTitle, String stage,
            String courtCaseNumber, Integer courtCaseYear, LocalDate judgmentDate, String status, boolean tracked,
            LocalDate dueOn, boolean late, UUID responsibleMemberId, String responsibleMemberName, String executingParty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountRequired) {
    }
}
