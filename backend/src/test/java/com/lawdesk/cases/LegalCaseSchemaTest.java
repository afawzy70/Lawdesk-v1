package com.lawdesk.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;

/**
 * LD-012 database rules (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012 criterion 7; QA-0094): the schema refuses what
 * the API refuses, also when the application is bypassed.
 */
@SpringBootTest
class LegalCaseSchemaTest extends PostgresTestSupport {

    private static final String ACTOR = "00000000-0000-4000-8000-000000000001";

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private int insertCase(String referenceNo, String title, String caseType, String status) {
        return jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values (?, ?, ?, current_date, ?, ?::uuid, ?::uuid)""", referenceNo, title, caseType, status, ACTOR,
                ACTOR);
    }

    @Test
    void appliesDefaultsForIdentifierStatusVersionAndTimestamps() {
        jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('R-1', 'T', 'CIVIL', current_date, ?::uuid, ?::uuid)""", ACTOR, ACTOR);

        Map<String, Object> row = jdbc.queryForMap("select * from legal_case where reference_no = 'R-1'");
        assertThat(row.get("id")).isInstanceOf(UUID.class);
        assertThat(row).containsEntry("status", "OPEN").containsEntry("version", 0L);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void acceptsEveryCaseTypeAndStatusTheApplicationKnows() {
        int n = 0;
        for (CaseType type : CaseType.values()) {
            for (CaseStatus status : CaseStatus.values()) {
                assertThat(insertCase("R-" + n++, "T", type.name(), status.name())).isEqualTo(1);
            }
        }
    }

    @Test
    void refusesUnknownCaseTypeOrStatus() {
        assertThatThrownBy(() -> insertCase("R-1", "T", "TAX", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCase("R-1", "T", "CIVIL", "DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesBlankOrOverlongTitle() {
        assertThatThrownBy(() -> insertCase("R-1", "   ", "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCase("R-1", "t".repeat(201), "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertCase("R-1", "t".repeat(200), "CIVIL", "OPEN")).isEqualTo(1);
    }

    @Test
    void refusesMalformedOrOverlongReferenceNumbers() {
        assertThatThrownBy(() -> insertCase("", "T", "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCase("a b", "T", "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCase("r".repeat(41), "T", "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referenceNumbersAreUniqueIgnoringCase() {
        insertCase("Abc-1", "T", "CIVIL", "OPEN");

        assertThatThrownBy(() -> insertCase("aBC-1", "T", "CIVIL", "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actorColumnsAreRequired() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, updated_by)
                values ('R-1', 'T', 'CIVIL', current_date, ?::uuid)""", ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timelineEventsBelongToAnExistingCaseAndHaveAValidTypeAndSummary() {
        insertCase("R-1", "T", "CIVIL", "OPEN");
        UUID caseId = jdbc.queryForObject("select id from legal_case", UUID.class);
        String insertEvent = "insert into case_event (case_id, type, summary, created_by) values (?, ?, ?, ?::uuid)";

        assertThat(jdbc.update(insertEvent, caseId, "CASE_CREATED", "ok", ACTOR)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insertEvent, UUID.randomUUID(), "CASE_CREATED", "ok", ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertEvent, caseId, "case created", "ok", ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertEvent, caseId, "CASE_CREATED", " ", ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertEvent, caseId, "CASE_CREATED", "s".repeat(501), ACTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCaseWithTimelineEventsCannotBeDeletedSilently() {
        insertCase("R-1", "T", "CIVIL", "OPEN");
        UUID caseId = jdbc.queryForObject("select id from legal_case", UUID.class);
        jdbc.update("insert into case_event (case_id, type, summary, created_by) values (?, 'X', 's', ?::uuid)",
                caseId, ACTOR);

        assertThatThrownBy(() -> jdbc.update("delete from legal_case where id = ?", caseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
