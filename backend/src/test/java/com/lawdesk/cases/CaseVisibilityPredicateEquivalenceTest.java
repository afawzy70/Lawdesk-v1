package com.lawdesk.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;

/**
 * LD-058: the predicates {@link CaseAccessPolicy} adds to queries are written for speed (cheap column tests first, the
 * database function last, and the rule evaluated once per case rather than once per child row). They must select exactly
 * the cases the one rule, the database function {@code case_visible}, selects. This test compares them, for every case and
 * several users and scopes, over cases of every shape: members, creator, restricted, typed, in a unit subtree, granted
 * (active, revoked, expired) and with a task (open and done).
 */
@SpringBootTest
class CaseVisibilityPredicateEquivalenceTest extends PostgresTestSupport {

    private static final UUID SYSTEM = UUID.fromString("00000000-0000-4000-8000-0000000000e5");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JdbcClient client;

    @Autowired
    CaseAccessPolicy access;

    private final List<UUID> users = new ArrayList<>();
    private UUID top;
    private UUID child;
    private UUID otherTop;

    private UUID user(String name) {
        UUID id = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, ?, ?) returning id""", UUID.class, "eqv." + name + UUID.randomUUID().toString().substring(0, 6),
                name + "@example.invalid", name, SYSTEM, SYSTEM);
        users.add(id);
        return id;
    }

    private UUID unit(UUID parent, String name) {
        return jdbc.queryForObject("""
                insert into org_unit (tree, parent_id, level, name_ar, created_by, updated_by)
                values ('LEGAL', ?, ?, ?, ?, ?) returning id""", UUID.class, parent, parent == null ? "SECTOR" : "DEPARTMENT",
                "eqv-" + name + UUID.randomUUID(), SYSTEM, SYSTEM);
    }

    private UUID legalCase(String type, String confidentiality, UUID owning, UUID primary, UUID alternate, UUID creator) {
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by,
                    primary_member_id, alternate_member_id, confidentiality, owning_unit_id)
                values (?, 'eqv', ?, current_date, 'OPEN', ?, ?, ?, ?, ?, ?) returning id""", UUID.class,
                "EQV-" + UUID.randomUUID().toString().substring(0, 12), type, creator, creator, primary, alternate, confidentiality,
                owning);
    }

    @BeforeEach
    void setUp() {
        top = unit(null, "top");
        child = unit(top, "child");
        otherTop = unit(null, "other");
    }

    @AfterEach
    void clean() {
        TestActors.signOut();
        jdbc.update("delete from task where title = 'eqv'");
        jdbc.update("delete from case_grant where case_id in (select id from legal_case where reference_no like 'EQV-%')");
        jdbc.update("delete from legal_case where reference_no like 'EQV-%'");
        for (UUID id : List.of(child, top, otherTop)) {
            jdbc.update("delete from org_unit where id = ?", id);
        }
        for (UUID id : users) {
            jdbc.update("delete from app_user where id = ?", id);
        }
    }

    @Test
    void theFastPredicatesSelectExactlyTheCasesTheRuleSelects() {
        UUID me = user("me");
        UUID other = user("other");
        UUID creator = user("creator");
        for (String type : List.of("CIVIL", "LABOR")) {
            for (String confidentiality : List.of("NORMAL", "RESTRICTED")) {
                for (UUID owning : new UUID[] {null, top, child, otherTop}) {
                    legalCase(type, confidentiality, owning, other, null, creator);
                    legalCase(type, confidentiality, owning, me, null, creator);
                    legalCase(type, confidentiality, owning, other, me, creator);
                    legalCase(type, confidentiality, owning, null, null, me);
                }
            }
        }
        UUID granted = legalCase("CIVIL", "RESTRICTED", null, other, null, creator);
        UUID revoked = legalCase("CIVIL", "RESTRICTED", null, other, null, creator);
        UUID expired = legalCase("CIVIL", "RESTRICTED", null, other, null, creator);
        UUID withOpenTask = legalCase("LABOR", "RESTRICTED", null, other, null, creator);
        UUID withDoneTask = legalCase("LABOR", "RESTRICTED", null, other, null, creator);
        jdbc.update("insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'r', ?)", granted, me, SYSTEM);
        jdbc.update("insert into case_grant (case_id, user_id, level, reason, granted_by, revoked_at, revoked_by) values (?, ?, 'VIEW', 'r', ?, now(), ?)",
                revoked, me, SYSTEM, SYSTEM);
        jdbc.update("insert into case_grant (case_id, user_id, level, reason, granted_by, expires_at) values (?, ?, 'VIEW', 'r', ?, ?)",
                expired, me, SYSTEM, Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        jdbc.update("insert into task (case_id, title, assignee_id, status, created_by, updated_by) values (?, 'eqv', ?, 'NEW', ?, ?)",
                withOpenTask, me, SYSTEM, SYSTEM);
        jdbc.update("insert into task (case_id, title, assignee_id, status, completed_at, created_by, updated_by) values (?, 'eqv', ?, 'COMPLETED', now(), ?, ?)",
                withDoneTask, me, SYSTEM, SYSTEM);

        List<List<String>> scopes = List.of(List.of(), List.of("ALL"), List.of("TYPE:CIVIL"), List.of("UNIT:" + top),
                List.of("UNIT:" + child), List.of("UNIT:" + otherTop), List.of("TYPE:LABOR", "UNIT:" + child),
                List.of("ALL", "TYPE:CIVIL"));
        int compared = 0;
        for (UUID who : List.of(me, other, creator, UUID.randomUUID())) {
            for (List<String> scope : scopes) {
                TestActors.signInScoped(who, scope, Permission.CASE_VIEW);
                CaseAccessPolicy.Scope s = access.scope();
                Set<UUID> rule = new HashSet<>(client.sql("select id from legal_case where reference_no like 'EQV-%'"
                        + " and case_visible(id, :me, :all, :units, :types)").param("me", s.me()).param("all", s.all())
                        .param("units", s.units()).param("types", s.types()).query(UUID.class).list());
                CaseAccessPolicy.Predicate onCases = access.viewable("legal_case");
                Set<UUID> direct = new HashSet<>(client.sql("select id from legal_case where reference_no like 'EQV-%' and "
                        + onCases.sql()).params(onCases.params()).query(UUID.class).list());
                CaseAccessPolicy.Predicate onChildren = access.visibleCaseIds("t.case_id");
                // One child row per case, so a predicate evaluated per child row and per case give the same answer.
                jdbc.update("delete from task where title = 'eqv-probe'");
                jdbc.update("insert into task (case_id, title, assignee_id, status, created_by, updated_by) "
                        + "select id, 'eqv-probe', ?, 'CANCELLED', ?, ? from legal_case where reference_no like 'EQV-%'",
                        UUID.randomUUID(), SYSTEM, SYSTEM);
                Set<UUID> viaChildren = new HashSet<>(client.sql("select t.case_id from task t where t.title = 'eqv-probe' and "
                        + onChildren.sql()).params(onChildren.params()).query(UUID.class).list());
                jdbc.update("delete from task where title = 'eqv-probe'");
                assertThat(direct).as("viewable() for %s with %s", who, scope).isEqualTo(rule);
                assertThat(viaChildren).as("visibleCaseIds() for %s with %s", who, scope).isEqualTo(rule);
                compared++;
            }
        }
        assertThat(compared).isEqualTo(32);
        // the shapes that matter were really present: some cases are visible and some are not, for the member
        TestActors.signInScoped(me, List.of(), Permission.CASE_VIEW);
        CaseAccessPolicy.Predicate mine = access.viewable("legal_case");
        List<UUID> visible = client.sql("select id from legal_case where reference_no like 'EQV-%' and " + mine.sql())
                .params(mine.params()).query(UUID.class).list();
        assertThat(visible).contains(granted, withOpenTask).doesNotContain(revoked, expired, withDoneTask);
    }
}
