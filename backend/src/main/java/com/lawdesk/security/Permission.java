package com.lawdesk.security;

/**
 * Every permission the application knows (architecture/CONTRACTS.md §3). Granted authorities are these codes; roles
 * are database rows grouping them. Later phases add their codes here, and {@code src/api/permissions.ts} mirrors the
 * list for the frontend (kept equal by tools/tests/test_contracts.py).
 */
public enum Permission {
    USER_VIEW,
    USER_MANAGE,
    ROLE_VIEW,
    ROLE_MANAGE,
    AUDIT_VIEW,
    CASE_VIEW,
    CASE_CREATE,
    CASE_EDIT,
    CASE_DELETE,
    CASE_STATUS_CHANGE,
    PARTY_MANAGE,
    COURT_VIEW,
    COURT_MANAGE,
    PROCEEDING_VIEW,
    PROCEEDING_MANAGE,
    JUDGMENT_VIEW,
    JUDGMENT_MANAGE;

    /** {@code @PreAuthorize} expressions, one per permission: {@code @PreAuthorize(Permission.Codes.CASE_CREATE)}. */
    public static final class Codes {
        public static final String USER_VIEW = "hasAuthority('USER_VIEW')";
        public static final String USER_MANAGE = "hasAuthority('USER_MANAGE')";
        public static final String ROLE_VIEW = "hasAuthority('ROLE_VIEW')";
        public static final String ROLE_MANAGE = "hasAuthority('ROLE_MANAGE')";
        public static final String AUDIT_VIEW = "hasAuthority('AUDIT_VIEW')";
        public static final String CASE_VIEW = "hasAuthority('CASE_VIEW')";
        public static final String CASE_CREATE = "hasAuthority('CASE_CREATE')";
        public static final String CASE_EDIT = "hasAuthority('CASE_EDIT')";
        public static final String CASE_DELETE = "hasAuthority('CASE_DELETE')";
        public static final String CASE_STATUS_CHANGE = "hasAuthority('CASE_STATUS_CHANGE')";
        public static final String PARTY_MANAGE = "hasAuthority('PARTY_MANAGE')";
        public static final String COURT_VIEW = "hasAuthority('COURT_VIEW')";
        public static final String COURT_MANAGE = "hasAuthority('COURT_MANAGE')";
        public static final String PROCEEDING_VIEW = "hasAuthority('PROCEEDING_VIEW')";
        public static final String PROCEEDING_MANAGE = "hasAuthority('PROCEEDING_MANAGE')";
        public static final String JUDGMENT_VIEW = "hasAuthority('JUDGMENT_VIEW')";
        public static final String JUDGMENT_MANAGE = "hasAuthority('JUDGMENT_MANAGE')";

        private Codes() {
        }
    }
}
