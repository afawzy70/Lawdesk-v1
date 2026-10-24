package com.lawdesk.reports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseStatus;

/** docs/delivery/PHASE-6-ACCEPTANCE.md, common rule 3: the status groups come from the cases module. */
class StatusGroupTest {

    @Test
    void closedAndArchivedCasesAreClosedEveryOtherStatusIsOpen() {
        assertThat(StatusGroup.OPEN.statuses()).containsExactly(CaseStatus.OPEN, CaseStatus.ACTIVE,
                CaseStatus.SUSPENDED);
        assertThat(StatusGroup.CLOSED.statuses()).containsExactly(CaseStatus.CLOSED, CaseStatus.ARCHIVED);
        assertThat(StatusGroup.of(CaseStatus.SUSPENDED)).isEqualTo(StatusGroup.OPEN);
    }
}
