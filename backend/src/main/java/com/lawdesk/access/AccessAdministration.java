package com.lawdesk.access;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseType;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Who sees what, administered (owner decision D3, LD-DOC-04): explicit grants on one case, the scopes of roles and users,
 * and the confidentiality of a case. Every change is audited with the subject and the object, and none can be made by a
 * user for themselves, so the system administrator cannot give the administrator account a view of case content by
 * administering it: another holder of the right has to, and the audit shows who.
 */
@Service
class AccessAdministration {

    static final String SELF_GRANT = "ACCESS_SELF_GRANT";
    static final String SCOPE_FOR_ADMIN_ROLE = "ACCESS_SCOPE_FOR_ADMIN_ROLE";
    static final String GRANT_USER_DISABLED = "ACCESS_GRANT_USER_DISABLED";
    static final String EXPIRY_PAST = "ACCESS_EXPIRY_PAST";
    static final String NO_ACTIVE_GRANT = "ACCESS_NO_ACTIVE_GRANT";
    static final String SCOPE_TARGET_UNKNOWN = "ACCESS_SCOPE_TARGET_UNKNOWN";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final AuditLog audit;

    AccessAdministration(JdbcClient jdbc, CurrentActor currentActor, CaseAccessPolicy access, CaseLookup cases,
            AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.access = access;
        this.cases = cases;
        this.audit = audit;
    }

    // ---- grants -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<AccessRequests.Grant> grants(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql(GRANT_SELECT + " where g.case_id = :id order by g.granted_at desc, g.id").param("id", caseId)
                .query(AccessAdministration::grant).list();
    }

    @Transactional
    AccessRequests.Grant grant(UUID caseId, AccessRequests.GrantCreate request) {
        ActorRef actor = currentActor.require();
        // Granting is a decision about who may work on a case: it needs the right to distribute that case.
        access.requireDistribute(caseId, Permission.CASE_GRANT_MANAGE);
        if (request.userId().equals(actor.id())) {
            throw new BusinessRuleException(SELF_GRANT, "a user cannot grant themself access");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(EXPIRY_PAST, "the grant would already be expired");
        }
        boolean enabled = jdbc.sql("select enabled from app_user where id = :id").param("id", request.userId())
                .query(Boolean.class).optional().orElseThrow(() -> new FieldErrorException("userId", "Exists"));
        if (!enabled) {
            throw new BusinessRuleException(GRANT_USER_DISABLED, "the user is disabled");
        }
        UUID id = jdbc.sql("""
                        insert into case_grant (case_id, user_id, level, reason, granted_by, expires_at)
                        values (:case, :user, :level, :reason, :actor, :expires) returning id""")
                .param("case", caseId).param("user", request.userId()).param("level", request.level())
                .param("reason", request.reason().strip()).param("actor", actor.id())
                .param("expires", request.expiresAt() == null ? null : java.sql.Timestamp.from(request.expiresAt()))
                .query(UUID.class).single();
        audit.recordWithChange("CASE_GRANT_CREATE", actor.id(), actor.username(), "CASE", caseId.toString(),
                request.level() + " user=" + request.userId());
        cases.recordEvent(caseId, "CASE_ACCESS_GRANTED", "Access granted: " + request.level());
        return grantById(id);
    }

    @Transactional
    void revoke(UUID caseId, UUID grantId) {
        ActorRef actor = currentActor.require();
        access.requireDistribute(caseId, Permission.CASE_GRANT_MANAGE);
        int changed = jdbc.sql("""
                        update case_grant set revoked_at = now(), revoked_by = :actor
                        where id = :id and case_id = :case and revoked_at is null""")
                .param("actor", actor.id()).param("id", grantId).param("case", caseId).update();
        if (changed == 0) {
            if (jdbc.sql("select exists (select 1 from case_grant where id = :id and case_id = :case)")
                    .param("id", grantId).param("case", caseId).query(Boolean.class).single()) {
                throw new BusinessRuleException(NO_ACTIVE_GRANT, "the grant was withdrawn already");
            }
            throw new NotFoundException("grant");
        }
        audit.recordWithChange("CASE_GRANT_REVOKE", actor.id(), actor.username(), "CASE", caseId.toString(),
                "grant=" + grantId);
        cases.recordEvent(caseId, "CASE_ACCESS_REVOKED", "Access withdrawn");
    }

    private AccessRequests.Grant grantById(UUID id) {
        return jdbc.sql(GRANT_SELECT + " where g.id = :id").param("id", id).query(AccessAdministration::grant).single();
    }

    private static final String GRANT_SELECT = """
            select g.id, g.case_id, g.user_id, u.display_name, g.level, g.reason, g.granted_by, g.granted_at,
                   g.expires_at, g.revoked_at, g.revoked_by
            from case_grant g join app_user u on u.id = g.user_id""";

    private static AccessRequests.Grant grant(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new AccessRequests.Grant(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("level"),
                rs.getString("reason"), rs.getObject("granted_by", UUID.class), instant(rs, "granted_at"),
                instant(rs, "expires_at"), instant(rs, "revoked_at"), rs.getObject("revoked_by", UUID.class));
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    // ---- scopes -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<AccessRequests.Scope> scopes(String subjectType, String subjectRef) {
        return jdbc.sql("""
                        select id, subject_type, subject_ref, scope_type, scope_ref, created_at from access_scope
                        where (cast(:type as varchar) is null or subject_type = :type)
                          and (cast(:ref as varchar) is null or subject_ref = :ref)
                        order by subject_type, subject_ref, scope_type, scope_ref""")
                .param("type", subjectType).param("ref", subjectRef).query(AccessAdministration::scope).list();
    }

    @Transactional
    AccessRequests.Scope addScope(AccessRequests.ScopeCreate request) {
        ActorRef actor = currentActor.require();
        boolean needsRef = request.scopeType().equals("ORG_UNIT") || request.scopeType().equals("CASE_TYPE");
        if (needsRef != (request.scopeRef() != null)) {
            throw new FieldErrorException("scopeRef", needsRef ? "NotNull" : "Null");
        }
        if (request.subjectType().equals("USER")) {
            UUID user = parseId(request.subjectRef(), "subjectRef");
            if (user.equals(actor.id())) {
                throw new BusinessRuleException(SELF_GRANT, "a user cannot widen their own scope");
            }
            exists("select exists (select 1 from app_user where id = :id)", user, "subjectRef");
        } else {
            if (request.subjectRef().equals("ADMIN")) {
                throw new BusinessRuleException(SCOPE_FOR_ADMIN_ROLE,
                        "administering the system does not give a view of case content");
            }
            exists("select exists (select 1 from app_role where code = :id)", request.subjectRef(), "subjectRef");
        }
        if (request.scopeType().equals("ORG_UNIT")) {
            exists("select exists (select 1 from org_unit where id = :id and tree = 'LEGAL')",
                    parseId(request.scopeRef(), "scopeRef"), "scopeRef");
        } else if (request.scopeType().equals("CASE_TYPE")) {
            try {
                CaseType.valueOf(request.scopeRef());
            } catch (IllegalArgumentException e) {
                throw new FieldErrorException("scopeRef", "Pattern");
            }
        }
        try {
            UUID id = jdbc.sql("""
                            insert into access_scope (subject_type, subject_ref, scope_type, scope_ref, created_by)
                            values (:type, :ref, :scope, :scopeRef, :actor) returning id""")
                    .param("type", request.subjectType()).param("ref", request.subjectRef())
                    .param("scope", request.scopeType()).param("scopeRef", request.scopeRef())
                    .param("actor", actor.id()).query(UUID.class).single();
            audit.recordWithChange("ACCESS_SCOPE_ADD", actor.id(), actor.username(), "ACCESS_SCOPE", id.toString(),
                    request.subjectType() + ":" + request.subjectRef() + " " + request.scopeType());
            return jdbc.sql("select id, subject_type, subject_ref, scope_type, scope_ref, created_at from access_scope"
                    + " where id = :id").param("id", id).query(AccessAdministration::scope).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("this scope exists already");
        }
    }

    @Transactional
    void removeScope(UUID id) {
        ActorRef actor = currentActor.require();
        var row = jdbc.sql("select subject_type, subject_ref, scope_type from access_scope where id = :id")
                .param("id", id).query((rs, n) -> rs.getString(1) + ":" + rs.getString(2) + " " + rs.getString(3))
                .optional().orElseThrow(() -> new NotFoundException("scope"));
        jdbc.sql("delete from access_scope where id = :id").param("id", id).update();
        audit.recordWithChange("ACCESS_SCOPE_REMOVE", actor.id(), actor.username(), "ACCESS_SCOPE", id.toString(), row);
    }

    private void exists(String sql, Object id, String field) {
        if (!jdbc.sql(sql).param("id", id).query(Boolean.class).single()) {
            throw new BusinessRuleException(SCOPE_TARGET_UNKNOWN, field + " does not name an existing record");
        }
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException(field, "Pattern");
        }
    }

    private static AccessRequests.Scope scope(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new AccessRequests.Scope(rs.getObject("id", UUID.class), rs.getString("subject_type"),
                rs.getString("subject_ref"), rs.getString("scope_type"), rs.getString("scope_ref"),
                instant(rs, "created_at"));
    }

    // ---- confidentiality --------------------------------------------------------------------------------------

    /** Marks a case RESTRICTED or NORMAL. Restricting needs the right to grant on the case; every change has a reason. */
    @Transactional
    void setConfidentiality(UUID caseId, AccessRequests.Confidentiality request) {
        ActorRef actor = currentActor.require();
        access.requireDistribute(caseId, Permission.CASE_GRANT_MANAGE);
        // The timeline entry first: restricting a case can end the right of the one who restricts it (a supervisor by
        // scope loses sight of it), and the entry is written under their rights at the time they act.
        long current = jdbc.sql("select version from legal_case where id = :id").param("id", caseId)
                .query(Long.class).single();
        if (current != request.version()) {
            throw new ConflictException("case " + caseId + " has changed since version " + request.version());
        }
        String now = jdbc.sql("select confidentiality from legal_case where id = :id").param("id", caseId)
                .query(String.class).single();
        if (now.equals(request.level())) {
            return;
        }
        cases.recordEvent(caseId, "CASE_CONFIDENTIALITY_CHANGED", "Confidentiality: " + request.level());
        int changed = jdbc.sql("""
                        update legal_case set confidentiality = :level, version = version + 1, updated_at = now(),
                            updated_by = :actor
                        where id = :id and version = :version and confidentiality <> :level""")
                .param("level", request.level()).param("actor", actor.id()).param("id", caseId)
                .param("version", request.version()).update();
        if (changed == 0) {
            throw new ConflictException("case " + caseId + " has changed concurrently");
        }
        audit.recordWithChange("CASE_CONFIDENTIALITY_CHANGE", actor.id(), actor.username(), "CASE", caseId.toString(),
                request.level());
    }
}
