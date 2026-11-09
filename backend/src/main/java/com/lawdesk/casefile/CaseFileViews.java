package com.lawdesk.casefile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawdesk.cases.MemberRef;

/** The executive summary and the unified timeline of a case (LD-053, requirements section 21). */
final class CaseFileViews {

    private CaseFileViews() {
    }

    record Party(String name, String role) {
    }

    record Unit(String name, boolean primary) {
    }

    record Procedure(String type, String summary, Instant occurredAt) {
    }

    record HearingBrief(UUID id, LocalDate hearingDate, @JsonFormat(pattern = "HH:mm") LocalTime hearingTime, String status,
            String purpose, String kind, String decision, String adjournmentReason, boolean noNextDate) {
    }

    /** Counts of the hearings with the last held one and the next scheduled one; adjournments keep every hearing. */
    record Hearings(long total, long held, long adjourned, long cancelled, long scheduled, long awaitingResult,
            long followUp, HearingBrief lastHearing, HearingBrief nextHearing) {
    }

    record JudgmentBrief(UUID id, UUID proceedingId, LocalDate judgmentDate, String outcome, String summary, boolean finalJudgment) {
    }

    record RiskBrief(UUID id, int versionNo, String level, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal potentialLoss,
            Instant assessedAt, long openRemediation) {
    }

    record PendingRequest(UUID id, String requestNo, String subject, String unitName, String status, LocalDate dueOn,
            boolean overdue) {
    }

    record InfoRequests(long open, long overdue, long incompleteReplies, List<PendingRequest> pending) {
    }

    record TaskBrief(UUID id, String title, LocalDate dueDate, String status, boolean overdue) {
    }

    record Tasks(long open, long overdue, List<TaskBrief> items) {
    }

    record Links(long count, long hiddenCount) {
    }

    record DeadlineBrief(UUID id, String action, LocalDate dueOn, String calcStatus, boolean overdue) {
    }

    /** Open legal deadlines of the case; {@code unapproved} are manual or proposed dates nobody has approved yet. */
    record Deadlines(long open, long overdue, long unapproved, List<DeadlineBrief> items) {
    }

    /**
     * The summary, assembled from stored data at the moment of the request; nothing is generated or cached. A section is
     * absent (null) when the user may not open that area; {@code pendingModules} names what has no stored data yet.
     */
    record Summary(UUID caseId, String referenceNo, String automatedNo, String title, String subject, String caseType,
            String status, String authorityPosition,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal claimedAmount, MemberRef primaryMember,
            MemberRef alternateMember, List<Party> parties, List<Unit> involvedUnits, Procedure lastProcedure,
            Hearings hearings, JudgmentBrief lastJudgment, RiskBrief risk, InfoRequests infoRequests, Tasks tasks,
            Links links, List<String> pendingModules, Deadlines deadlines) {
    }

    /** One entry of the unified timeline; {@code refType}/{@code refId} name the record it comes from. */
    record Entry(Instant at, String kind, String status, String summary, String refType, UUID refId, boolean isFuture,
            boolean needsAction, UUID actorId, String actorName) {
    }

    record Timeline(List<Entry> items, int page, int size, long totalItems, int totalPages, boolean deadlinesAvailable) {
    }
}
