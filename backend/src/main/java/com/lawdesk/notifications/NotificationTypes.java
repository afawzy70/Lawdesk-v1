package com.lawdesk.notifications;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The kinds of in-app notification (requirements section 32, owner decision D11). The first eleven are the list of the
 * requirements, in its order; the others are system notices the same centre carries. A user may switch any of them off
 * for themselves (preferences); none is ever sent outside the system.
 */
public final class NotificationTypes {

    // The eleven of section 32.
    public static final String CASE_NEW = "CASE_NEW";
    public static final String HEARING_UPCOMING = "HEARING_UPCOMING";
    public static final String EXPERT_SESSION_UPCOMING = "EXPERT_SESSION_UPCOMING";
    public static final String APPEAL_DEADLINE = "APPEAL_DEADLINE";
    public static final String TASK_OVERDUE = "TASK_OVERDUE";
    public static final String HEARING_NO_RESULT = "HEARING_NO_RESULT";
    public static final String INFO_REQUEST_OVERDUE = "INFO_REQUEST_OVERDUE";
    public static final String JUDGMENT_NEW = "JUDGMENT_NEW";
    public static final String DOCUMENT_MISSING = "DOCUMENT_MISSING";
    public static final String DATE_CONFLICT = "DATE_CONFLICT";
    public static final String RISK_HIGH = "RISK_HIGH";
    // System notices.
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_DUE_SOON = "TASK_DUE_SOON";
    public static final String INFO_REQUEST_ESCALATED = "INFO_REQUEST_ESCALATED";
    public static final String HEARING_NO_NEXT_DATE = "HEARING_NO_NEXT_DATE";

    /** One kind. {@code core} marks the eleven of the requirements; {@code position} is their order there. */
    public record Type(String code, boolean core, int position) {
    }

    public static final List<Type> ALL = List.of(
            new Type(CASE_NEW, true, 1), new Type(HEARING_UPCOMING, true, 2),
            new Type(EXPERT_SESSION_UPCOMING, true, 3), new Type(APPEAL_DEADLINE, true, 4),
            new Type(TASK_OVERDUE, true, 5), new Type(HEARING_NO_RESULT, true, 6),
            new Type(INFO_REQUEST_OVERDUE, true, 7), new Type(JUDGMENT_NEW, true, 8),
            new Type(DOCUMENT_MISSING, true, 9), new Type(DATE_CONFLICT, true, 10), new Type(RISK_HIGH, true, 11),
            new Type(TASK_ASSIGNED, false, 12), new Type(TASK_DUE_SOON, false, 13),
            new Type(INFO_REQUEST_ESCALATED, false, 14), new Type(HEARING_NO_NEXT_DATE, false, 15));

    private static final Set<String> CODES = ALL.stream().map(Type::code).collect(Collectors.toUnmodifiableSet());

    private NotificationTypes() {
    }

    public static boolean known(String code) {
        return CODES.contains(code);
    }
}
