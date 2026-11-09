package com.lawdesk.followup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the follow-up hourly. Tests switch it off ({@code lawdesk.followup.schedule-enabled=false}) and call the run. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "lawdesk.followup.schedule-enabled", havingValue = "true", matchIfMissing = true)
class FollowUpSchedule {

    private static final Logger log = LoggerFactory.getLogger(FollowUpSchedule.class);

    private final FollowUpRun run;

    FollowUpSchedule(FollowUpRun run) {
        this.run = run;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    void tick() {
        try {
            FollowUpRun.Result result = run.run();
            log.info("follow-up run: {} requests marked overdue, {} tasks marked late, {} notifications",
                    result.overdueRequests(), result.lateTasks(), result.notifications());
        } catch (RuntimeException e) {
            log.error("follow-up run failed", e);
        }
    }
}
