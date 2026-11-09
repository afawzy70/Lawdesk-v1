package com.lawdesk.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One dated entry of the calendar. Entries are derived, never stored as such: a hearing, a task, an expert session, an
 * information request's date or a manual date appears here from its own record, so there is nothing to keep in step.
 *
 * @param id        {@code refType:refId}, unique in a response
 * @param type      what it is on the calendar: HEARING, EXPERT_SESSION, INVESTIGATION, TASK, DEADLINE, MEETING, INFO_REQUEST_DUE,
 *                  MEMO_DUE, APPEAL_DEADLINE, ENFORCEMENT or OTHER
 * @param detail    a refinement a screen may show (the hearing's purpose, the task's priority, the manual date's kind)
 * @param stage     the stage of the proceeding of a hearing (FIRST_INSTANCE, APPEAL, CASSATION ...), else null
 * @param date      the Kuwait day
 * @param ownerId   whose calendar it is in: the responsible member, the assignee, the owner of the date
 * @param recipients who is told about it in the system (owner and the case's members); not part of the answer
 */
public record CalendarEntry(String id, String type, String refType, UUID refId, UUID caseId, String caseReferenceNo,
        String caseTitle, String caseStatus, String title, String detail, String stage, LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean allDay, Instant startsAt, Instant endsAt, UUID ownerId, String ownerName, String status,
        UUID alternateMemberId, String alternateMemberName, boolean conflict, boolean closedCaseFuture,
        @JsonIgnore List<UUID> recipients) {

    /** The types an entry can have, in the order a screen lists them. */
    public static final List<String> TYPES = List.of("HEARING", "EXPERT_SESSION", "INVESTIGATION", "TASK", "DEADLINE", "MEETING",
            "INFO_REQUEST_DUE", "MEMO_DUE", "APPEAL_DEADLINE", "ENFORCEMENT", "OTHER");

    /** Status values after which an entry no longer takes the member's time. */
    static boolean inactive(String status) {
        return "CANCELLED".equals(status) || "DONE".equals(status);
    }

    /** A dated entry; {@code startTime} null means a day-long entry. */
    public static CalendarEntry of(String type, String refType, UUID refId, UUID caseId, String caseReferenceNo,
            String caseTitle, String caseStatus, String title, String detail, String stage, LocalDate date,
            LocalTime startTime, LocalTime endTime, UUID ownerId, String status, UUID alternateMemberId,
            List<UUID> recipients) {
        Instant startsAt = CalendarTime.instant(date, startTime);
        Instant endsAt = startTime == null || endTime == null ? null : CalendarTime.instant(date, endTime);
        return new CalendarEntry(refType + ":" + refId, type, refType, refId, caseId, caseReferenceNo, caseTitle,
                caseStatus, title, detail, stage, date, startTime, endTime, startTime == null, startsAt, endsAt, ownerId,
                null, status, alternateMemberId, null, false, false, recipients);
    }

    CalendarEntry named(String owner, String alternate) {
        return new CalendarEntry(id, type, refType, refId, caseId, caseReferenceNo, caseTitle, caseStatus, title, detail,
                stage, date, startTime, endTime, allDay, startsAt, endsAt, ownerId, owner, status, alternateMemberId,
                alternate, conflict, closedCaseFuture, recipients);
    }

    CalendarEntry flagged(boolean inConflict, boolean closedFuture) {
        return new CalendarEntry(id, type, refType, refId, caseId, caseReferenceNo, caseTitle, caseStatus, title, detail,
                stage, date, startTime, endTime, allDay, startsAt, endsAt, ownerId, ownerName, status,
                alternateMemberId, alternateMemberName, inConflict, closedFuture, recipients);
    }
}
