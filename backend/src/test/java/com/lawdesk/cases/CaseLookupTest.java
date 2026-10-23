package com.lawdesk.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.ActorPrincipal;

/**
 * The {@link CaseLookup} port Workstream C codes against (architecture/CONTRACTS.md §5;
 * docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012 criterion 8).
 */
@SpringBootTest
class CaseLookupTest extends PostgresTestSupport {

    private static final UUID ACTOR = UUID.fromString("0f0f0f0f-2222-4abc-9def-000000000012");

    @Autowired
    CaseLookup cases;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    private UUID caseId;

    private record Principal(UUID id, String username) implements ActorPrincipal {
    }

    @BeforeEach
    void signInAndCreateACase() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new Principal(ACTOR, "clerk"), null, List.of()));
        caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values ('LD-2026-00042', 'Heirs of Hassan', 'FAMILY', current_date, 'ACTIVE', ?, ?)
                returning id""", UUID.class, ACTOR, ACTOR);
    }

    @AfterEach
    void signOutAndRemoveRows() {
        SecurityContextHolder.clearContext();
        CaseTestData.deleteAll(jdbc);
    }

    private List<Map<String, Object>> events() {
        return jdbc.queryForList("select * from case_event where case_id = ?", caseId);
    }

    @Test
    void findsTheSummaryOfACase() {
        assertThat(cases.find(caseId))
                .contains(new CaseSummary(caseId, "LD-2026-00042", "Heirs of Hassan", "ACTIVE"));
    }

    @Test
    void anUnknownCaseIsEmpty() {
        assertThat(cases.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void recordsAnEventInTheTimelineAsTheSignedInUser() {
        cases.recordEvent(caseId, "PROCEEDING_RECORDED", "First instance, Cairo Court, 1234/2026");

        assertThat(events()).singleElement().satisfies(event -> assertThat(event)
                .containsEntry("type", "PROCEEDING_RECORDED")
                .containsEntry("summary", "First instance, Cairo Court, 1234/2026")
                .containsEntry("created_by", ACTOR));
    }

    @Test
    void theEventJoinsTheCallersTransaction() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            cases.recordEvent(caseId, "PROCEEDING_RECORDED", "rolled back");
            throw new IllegalStateException("caller failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events()).isEmpty();
    }

    @Test
    void anEventForAnUnknownCaseIsNotFound() {
        assertThatThrownBy(() -> cases.recordEvent(UUID.randomUUID(), "PROCEEDING_RECORDED", "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void refusesMalformedTypesAndSummaries() {
        for (String type : new String[] {null, "", "lower", "1ABC", "A".repeat(65), "WITH SPACE"}) {
            assertThatThrownBy(() -> cases.recordEvent(caseId, type, "ok"))
                    .as("type %s", type).isInstanceOf(IllegalArgumentException.class);
        }
        for (String summary : new String[] {null, "", "   ", "s".repeat(501)}) {
            assertThatThrownBy(() -> cases.recordEvent(caseId, "PROCEEDING_RECORDED", summary))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        cases.recordEvent(caseId, "A".repeat(64), "s".repeat(500));
        assertThat(events()).hasSize(1);
    }

    @Test
    void needsASignedInUser() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> cases.recordEvent(caseId, "PROCEEDING_RECORDED", "x"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(events()).isEmpty();
    }
}
