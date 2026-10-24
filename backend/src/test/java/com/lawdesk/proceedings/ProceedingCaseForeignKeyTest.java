package com.lawdesk.proceedings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Cross-phase integration (architecture/CONTRACTS.md §2, §5): once B's legal_case is on develop, the database itself
 * guarantees that every proceeding belongs to an existing case, not only the CaseLookup check in the application.
 */
class ProceedingCaseForeignKeyTest extends JudicialApiTestSupport {

    private static final String INSERT = """
            insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                    created_by, updated_by)
            values (?, 'FIRST_INSTANCE', ?, ?, 2026, date '2026-01-02', ?, ?)""";

    @Test
    void aProceedingCannotReferToACaseThatDoesNotExist() throws Exception {
        UUID court = createCourt("FK-" + UUID.randomUUID().toString().substring(0, 6), "FIRST_INSTANCE");

        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), court, "1", CLERK, CLERK))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("proceeding_case_fk");
    }

    @Test
    void aProceedingOfAnExistingCaseIsStored() throws Exception {
        UUID caseId = createCase("Foreign key control");
        UUID court = createCourt("FK-" + UUID.randomUUID().toString().substring(0, 6), "FIRST_INSTANCE");

        assertThat(jdbc.update(INSERT, caseId, court, "2", CLERK, CLERK)).isEqualTo(1);
    }

    @Test
    void aCaseWithProceedingsCannotBeRemovedUnderThem() throws Exception {
        UUID caseId = createCase("Foreign key restrict");
        UUID court = createCourt("FK-" + UUID.randomUUID().toString().substring(0, 6), "FIRST_INSTANCE");
        jdbc.update(INSERT, caseId, court, "3", CLERK, CLERK);

        assertThatThrownBy(() -> jdbc.update("delete from legal_case where id = ?", caseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
