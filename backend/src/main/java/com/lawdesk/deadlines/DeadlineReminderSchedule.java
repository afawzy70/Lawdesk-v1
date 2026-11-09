package com.lawdesk.deadlines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the deadline reminders hourly. Scheduling itself is enabled by the follow-up module; tests switch both off
 * ({@code lawdesk.followup.schedule-enabled=false}) and call the run.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "lawdesk.followup.schedule-enabled", havingValue = "true", matchIfMissing = true)
class DeadlineReminderSchedule {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderSchedule.class);

    private final DeadlineReminderRun run;

    DeadlineReminderSchedule(DeadlineReminderRun run) {
        this.run = run;
    }

    @Scheduled(initialDelayString = "PT6M", fixedDelayString = "PT1H")
    void tick() {
        try {
            DeadlineRequests.ReminderRunResult result = run.run();
            log.info("deadline reminders: {} approaching, {} overdue", result.approaching(), result.overdue());
        } catch (RuntimeException e) {
            log.error("deadline reminders failed", e);
        }
    }
}
