package com.lawdesk.calendar;

import java.util.HashMap;
import java.util.Map;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * What a source may show: for a user, the cases {@link CaseAccessPolicy} lets them see and the records the permissions
 * allow; for the system (the follow-up run that raises notifications for the people concerned), everything.
 */
public final class CalendarScope {

    private final CaseAccessPolicy access;
    private final CurrentActor actor;

    private CalendarScope(CaseAccessPolicy access, CurrentActor actor) {
        this.access = access;
        this.actor = actor;
    }

    /** The signed-in user's scope. */
    public static CalendarScope of(CaseAccessPolicy access, CurrentActor actor) {
        return new CalendarScope(access, actor);
    }

    /** No restriction: for work the system does for everybody, never for an answer to a user. */
    public static CalendarScope system() {
        return new CalendarScope(null, null);
    }

    public boolean isSystem() {
        return access == null;
    }

    /** Whether the user holds {@code permission} (always true for the system). */
    public boolean may(Permission permission) {
        return isSystem() || actor.has(permission);
    }

    /** The rows whose case column {@code column} holds a case the user may see; a row without a case passes the case test. */
    public CaseAccessPolicy.Predicate cases(String column) {
        if (isSystem()) {
            return new CaseAccessPolicy.Predicate("true", Map.of());
        }
        return access.visibleCaseIds(column);
    }

    /**
     * Records that belong to a person and optionally to a case (tasks, manual dates): theirs as owner or creator, those of
     * visible cases, and — when they belong to no case — all of them for the department-wide scope.
     */
    public CaseAccessPolicy.Predicate records(String ownerColumn, String creatorColumn, String caseColumn) {
        if (isSystem()) {
            return new CaseAccessPolicy.Predicate("true", Map.of());
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds(caseColumn);
        Map<String, Object> params = new HashMap<>(visible.params());
        String sql = "(" + ownerColumn + " = :av_me or " + creatorColumn + " = :av_me or (" + caseColumn
                + " is null and :av_all) or (" + caseColumn + " is not null and " + visible.sql() + "))";
        return new CaseAccessPolicy.Predicate(sql, params);
    }
}
