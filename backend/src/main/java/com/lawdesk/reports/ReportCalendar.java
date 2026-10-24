package com.lawdesk.reports;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * "Today" for reports (docs/delivery/PHASE-6-ACCEPTANCE.md, common rule 7). ASSUMPTION: the firm works in Egypt, so
 * the current day and month are those of {@code Africa/Cairo}, whatever the server's own zone is.
 */
@Component
class ReportCalendar {

    static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private final Clock clock = Clock.system(ZONE);

    LocalDate today() {
        return LocalDate.now(clock);
    }
}
