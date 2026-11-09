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
    JUDGMENT_MANAGE,
    HEARING_VIEW,
    HEARING_MANAGE,
    EXPERT_VIEW,
    EXPERT_MANAGE,
    DOCUMENT_VIEW,
    DOCUMENT_MANAGE,
    TASK_VIEW,
    TASK_MANAGE,
    NOTE_MANAGE,
    REPORT_VIEW,
    REPORT_EXPORT,
    ASSIGN_CASE,
    CASE_EDIT_IN_SCOPE,
    CASE_GRANT_MANAGE,
    ORG_MANAGE,
    PARTY_ID_VIEW,
    LIBRARY_VIEW,
    LIBRARY_MANAGE,
    INFO_REQUEST_VIEW,
    INFO_REQUEST_MANAGE,
    INFO_REQUEST_ESCALATE,
    RISK_VIEW,
    RISK_MANAGE,
    CASE_LINK_MANAGE,
    CLAIM_VIEW,
    CLAIM_MANAGE,
    DEFENSE_VIEW,
    DEFENSE_MANAGE,
    EVIDENCE_VIEW,
    EVIDENCE_MANAGE,
    LOOKUP_MANAGE,
    SETTLEMENT_MANAGE,
    SETTLEMENT_APPROVE,
    DIRECTIVE_MANAGE,
    TEMPLATE_MANAGE,
    SEARCH_ALL,
    DEADLINE_VIEW,
    DEADLINE_MANAGE,
    DEADLINE_APPROVE,
    ENFORCEMENT_VIEW,
    ENFORCEMENT_MANAGE,
    MEMO_APPROVE,
    DOCUMENT_ARCHIVE_ADMIN,
    CALENDAR_VIEW,
    CALENDAR_MANAGE;

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
        public static final String HEARING_VIEW = "hasAuthority('HEARING_VIEW')";
        public static final String HEARING_MANAGE = "hasAuthority('HEARING_MANAGE')";
        public static final String EXPERT_VIEW = "hasAuthority('EXPERT_VIEW')";
        public static final String EXPERT_MANAGE = "hasAuthority('EXPERT_MANAGE')";
        public static final String DOCUMENT_VIEW = "hasAuthority('DOCUMENT_VIEW')";
        public static final String DOCUMENT_MANAGE = "hasAuthority('DOCUMENT_MANAGE')";
        public static final String TASK_VIEW = "hasAuthority('TASK_VIEW')";
        public static final String TASK_MANAGE = "hasAuthority('TASK_MANAGE')";
        public static final String NOTE_MANAGE = "hasAuthority('NOTE_MANAGE')";
        public static final String REPORT_VIEW = "hasAuthority('REPORT_VIEW')";
        public static final String REPORT_EXPORT = "hasAuthority('REPORT_EXPORT')";
        public static final String ASSIGN_CASE = "hasAuthority('ASSIGN_CASE')";
        public static final String CASE_EDIT_IN_SCOPE = "hasAuthority('CASE_EDIT_IN_SCOPE')";
        public static final String CASE_GRANT_MANAGE = "hasAuthority('CASE_GRANT_MANAGE')";
        public static final String ORG_MANAGE = "hasAuthority('ORG_MANAGE')";
        public static final String PARTY_ID_VIEW = "hasAuthority('PARTY_ID_VIEW')";
        public static final String LIBRARY_VIEW = "hasAuthority('LIBRARY_VIEW')";
        public static final String LIBRARY_MANAGE = "hasAuthority('LIBRARY_MANAGE')";
        public static final String INFO_REQUEST_VIEW = "hasAuthority('INFO_REQUEST_VIEW')";
        public static final String INFO_REQUEST_MANAGE = "hasAuthority('INFO_REQUEST_MANAGE')";
        public static final String INFO_REQUEST_ESCALATE = "hasAuthority('INFO_REQUEST_ESCALATE')";
        public static final String RISK_VIEW = "hasAuthority('RISK_VIEW')";
        public static final String RISK_MANAGE = "hasAuthority('RISK_MANAGE')";
        public static final String CASE_LINK_MANAGE = "hasAuthority('CASE_LINK_MANAGE')";
        public static final String CLAIM_VIEW = "hasAuthority('CLAIM_VIEW')";
        public static final String CLAIM_MANAGE = "hasAuthority('CLAIM_MANAGE')";
        public static final String DEFENSE_VIEW = "hasAuthority('DEFENSE_VIEW')";
        public static final String DEFENSE_MANAGE = "hasAuthority('DEFENSE_MANAGE')";
        public static final String EVIDENCE_VIEW = "hasAuthority('EVIDENCE_VIEW')";
        public static final String EVIDENCE_MANAGE = "hasAuthority('EVIDENCE_MANAGE')";
        public static final String LOOKUP_MANAGE = "hasAuthority('LOOKUP_MANAGE')";
        public static final String SETTLEMENT_MANAGE = "hasAuthority('SETTLEMENT_MANAGE')";
        public static final String SETTLEMENT_APPROVE = "hasAuthority('SETTLEMENT_APPROVE')";
        public static final String DIRECTIVE_MANAGE = "hasAuthority('DIRECTIVE_MANAGE')";
        public static final String TEMPLATE_MANAGE = "hasAuthority('TEMPLATE_MANAGE')";
        public static final String SEARCH_ALL = "hasAuthority('SEARCH_ALL')";
        public static final String DEADLINE_VIEW = "hasAuthority('DEADLINE_VIEW')";
        public static final String DEADLINE_MANAGE = "hasAuthority('DEADLINE_MANAGE')";
        public static final String DEADLINE_APPROVE = "hasAuthority('DEADLINE_APPROVE')";
        public static final String ENFORCEMENT_VIEW = "hasAuthority('ENFORCEMENT_VIEW')";
        public static final String ENFORCEMENT_MANAGE = "hasAuthority('ENFORCEMENT_MANAGE')";
        public static final String MEMO_APPROVE = "hasAuthority('MEMO_APPROVE')";
        public static final String DOCUMENT_ARCHIVE_ADMIN = "hasAuthority('DOCUMENT_ARCHIVE_ADMIN')";
        public static final String CALENDAR_VIEW = "hasAuthority('CALENDAR_VIEW')";
        public static final String CALENDAR_MANAGE = "hasAuthority('CALENDAR_MANAGE')";

        private Codes() {
        }
    }
}
