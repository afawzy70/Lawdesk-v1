package com.lawdesk.cases;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The one place that decides which cases a user may see, change and distribute (owner decision D3, LD-050; docs/design
 * LD-DOC-04). Permissions say what a user can do to cases in general; this says on which cases:
 *
 * <ul>
 *   <li><b>view</b> — the case's primary and alternate members, its creator, a user with a task on it, a user with an
 *       explicit grant, and — for cases that are not RESTRICTED — a user whose scope covers it (department-wide, an
 *       organisational unit and its subtree, or a case type). Holding a system permission, the administrator role
 *       included, never adds a case by itself.</li>
 *   <li><b>change</b> — the members, the creator while the case has no primary member, a user with a CONTRIBUTE grant, and a holder of {@code CASE_EDIT_IN_SCOPE} on a case
 *       in their scope. Viewing, a task or the creator role never allows a change.</li>
 *   <li><b>distribute</b> (assign members, change status) — a holder of {@code ASSIGN_CASE} / the status permission who
 *       can also change the case.</li>
 * </ul>
 *
 * The same rule is written once, as the database functions {@code case_visible} and {@code case_changeable}, and used for single cases, lists,
 * counts, reports, exports and every module that reads rows by case, so a screen cannot disagree with a download. A
 * case the user cannot see is reported as missing (404) so its existence is not disclosed; a case seen but not
 * changeable is refused (403). Everything is read from the database and the request's authorities on every call.
 */
@Component
public class CaseAccessPolicy {

    /** A SQL boolean expression with its named parameters, ready to be AND-ed into a query. */
    public record Predicate(String sql, Map<String, Object> params) {
    }

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;

    CaseAccessPolicy(JdbcClient jdbc, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
    }

    // ---- single cases -----------------------------------------------------------------------------------------

    /** True when the signed-in user may see this case; false for a case that does not exist. */
    @Transactional(readOnly = true)
    public boolean canView(UUID caseId) {
        return matches(caseId, viewable("c"));
    }

    /** @throws NotFoundException when the case does not exist or the user may not see it (404) */
    @Transactional(readOnly = true)
    public void requireView(UUID caseId) {
        if (!canView(caseId)) {
            throw new NotFoundException("case");
        }
    }

    /** True when the signed-in user may change the content of this case. */
    @Transactional(readOnly = true)
    public boolean mayChange(UUID caseId) {
        requireView(caseId);
        return matches(caseId, changeable("c"));
    }

    /**
     * @throws NotFoundException     when the user may not see the case (404)
     * @throws AccessDeniedException when the user sees the case but may not change it (403)
     */
    @Transactional(readOnly = true)
    public void requireChange(UUID caseId) {
        if (!mayChange(caseId)) {
            throw new AccessDeniedException("not a member of this case");
        }
    }

    /** @throws AccessDeniedException when the user lacks the permission or may not change the case (distributing it) */
    @Transactional(readOnly = true)
    public void requireDistribute(UUID caseId, Permission permission) {
        if (!currentActor.has(permission)) {
            requireView(caseId);
            throw new AccessDeniedException("missing " + permission);
        }
        requireChange(caseId);
    }

    /** True when the signed-in user is the primary or the alternate member of the case. */
    @Transactional(readOnly = true)
    public boolean isMember(UUID caseId) {
        UUID me = currentActor.require().id();
        return jdbc.sql("select exists (select 1 from legal_case where id = :id and (primary_member_id = :me"
                + " or alternate_member_id = :me))").param("id", caseId).param("me", me).query(Boolean.class).single();
    }

    private boolean matches(UUID caseId, Predicate predicate) {
        Map<String, Object> params = new HashMap<>(predicate.params());
        params.put("av_case", caseId);
        return jdbc.sql("select exists (select 1 from legal_case c where c.id = :av_case and " + predicate.sql() + ")")
                .params(params).query(Boolean.class).single();
    }

    // ---- SQL -------------------------------------------------------------------------------------------------

    /**
     * The arguments the database functions {@code case_visible} and {@code case_changeable} (V600) take for the signed-in
     * user. Public so that JPA queries can pass them with {@code function('case_visible', ...)}.
     */
    public record Scope(UUID me, boolean editInScope, boolean all, String units, String types) {
    }

    /** The scope of the signed-in user: their id, whether they may edit in scope, and their scope tokens. */
    public Scope scope() {
        Set<String> tokens = currentActor.scopes();
        String units = tokens.stream().filter(t -> t.startsWith("UNIT:")).map(t -> t.substring(5))
                .filter(t -> parse(t) != null).sorted().collect(Collectors.joining(","));
        String types = tokens.stream().filter(t -> t.startsWith("TYPE:")).map(t -> t.substring(5)).sorted()
                .collect(Collectors.joining(","));
        return new Scope(currentActor.require().id(), currentActor.has(Permission.CASE_EDIT_IN_SCOPE),
                tokens.contains("ALL"), units, types);
    }

    /** The cases of {@code alias} (an alias of {@code legal_case}) the signed-in user may see. */
    public Predicate viewable(String alias) {
        return new Predicate(fastVisible(alias), args(scope()));
    }

    /** The cases of {@code alias} the signed-in user may change. */
    public Predicate changeable(String alias) {
        Scope scope = scope();
        Map<String, Object> params = args(scope);
        params.put("av_edit", scope.editInScope());
        return new Predicate("case_changeable(" + alias + ".id, :av_me, :av_edit, :av_all, :av_units, :av_types)",
                params);
    }

    /**
     * The restriction every query over rows that belong to cases adds, such as hearings, documents, tasks and report
     * lists. {@code column} is an expression the caller controls. The rule is evaluated once per <em>case</em> (a hashed
     * semi-join), not once per row of the child table: a table with ten rows per case would otherwise run the database
     * function ten times as often.
     */
    public Predicate visibleCaseIds(String column) {
        return new Predicate(column + " in (select vc.id from legal_case vc where " + fastVisible("vc") + ")",
                args(scope()));
    }

    /**
     * {@code case_visible} written for speed, selecting exactly the same cases (CaseVisibilityPredicateEquivalenceTest). The
     * function stays the one definition of the rule; this only puts, before it, tests that are implied by it and cost a
     * column comparison: the user is the primary or alternate member or the creator, or the department-wide or case-type
     * scope covers a case that is not restricted. The function itself runs only for cases the user could still reach
     * through a unit scope, a grant or a task, so a member with few cases no longer pays it for every case in the
     * register. The grant and task tests here are deliberately broader than the rule (any grant, any task of the user): they
     * only decide whether to ask the function, never that a case is visible.
     */
    private static String fastVisible(String a) {
        return "(" + a + ".primary_member_id = :av_me or " + a + ".alternate_member_id = :av_me or " + a
                + ".created_by = :av_me or (:av_all and " + a + ".confidentiality <> 'RESTRICTED') or (:av_types <> ''"
                + " and " + a + ".confidentiality <> 'RESTRICTED' and " + a + ".case_type = any (string_to_array(:av_types, ',')))"
                + " or ((:av_units <> '' or " + a + ".id in (select cg.case_id from case_grant cg where cg.user_id = :av_me)"
                + " or " + a + ".id in (select ct.case_id from task ct where ct.assignee_id = :av_me and ct.case_id is not null))"
                + " and case_visible(" + a + ".id, :av_me, :av_all, :av_units, :av_types)))";
    }

    private static Map<String, Object> args(Scope scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("av_me", scope.me());
        params.put("av_all", scope.all());
        params.put("av_units", scope.units());
        params.put("av_types", scope.types());
        return params;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
