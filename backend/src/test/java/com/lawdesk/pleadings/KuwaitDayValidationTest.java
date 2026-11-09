package com.lawdesk.pleadings;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;

/**
 * "Not in the future" is judged on the Kuwait day: with the clock at 21:30 UTC (00:30 on the next day in Kuwait) a case, a
 * proceeding and a judgment dated with the Kuwait day are accepted and the day after is refused. Synthetic data only.
 */
class KuwaitDayValidationTest extends PleadingsTestSupport {

    /** 2026-11-09T21:30:00Z is 2026-11-10 00:30 in Kuwait. */
    @TestConfiguration
    static class PinnedClock {
        @Bean
        @Primary
        Clock pinnedClock() {
            return Clock.fixed(Instant.parse("2026-11-09T21:30:00Z"), ZoneId.of("Asia/Kuwait"));
        }
    }

    private RequestPostProcessor manager() {
        return TestActors.as(USER, "kday", Permission.CASE_VIEW, Permission.CASE_CREATE, Permission.JUDGMENT_VIEW,
                Permission.JUDGMENT_MANAGE, Permission.PROCEEDING_VIEW, Permission.PROCEEDING_MANAGE);
    }

    private String caseBody(String openedOn) {
        return json("title", "SYNTH-DOC Kuwait day", "caseType", "CIVIL", "openedOn", openedOn);
    }

    @Test
    void aCaseDatedWithTheKuwaitDayIsAcceptedAndTheDayAfterRefused() throws Exception {
        post(manager(), "/api/v1/cases", caseBody("2026-11-10")).andExpect(status().isCreated());
        post(manager(), "/api/v1/cases", caseBody("2026-11-11")).andExpect(status().isBadRequest());
    }

    @Test
    void aJudgmentDatedWithTheKuwaitDayIsAcceptedAndTheDayAfterRefused() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        String path = "/api/v1/proceedings/" + proceeding + "/judgment";
        post(manager(), path, json("judgmentDate", "2026-11-11", "outcome", "PARTIAL")).andExpect(status().isBadRequest());
        post(manager(), path, json("judgmentDate", "2026-11-10", "outcome", "PARTIAL")).andExpect(status().isCreated());
    }
}
