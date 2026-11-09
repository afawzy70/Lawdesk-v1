package com.lawdesk.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Hands waiting scans to the local recognition engine every minute. Tests switch it off
 * ({@code lawdesk.ocr.schedule-enabled=false}) and call {@link DocumentTextService#processPending} themselves.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "lawdesk.ocr.schedule-enabled", havingValue = "true", matchIfMissing = true)
class OcrSchedule {

    private static final Logger log = LoggerFactory.getLogger(OcrSchedule.class);
    private static final int BATCH = 5;

    private final DocumentTextService texts;

    OcrSchedule(DocumentTextService texts) {
        this.texts = texts;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    void tick() {
        try {
            int handled = texts.processPending(BATCH);
            if (handled > 0) {
                log.info("recognition: {} documents handled", handled);
            }
        } catch (RuntimeException e) {
            log.error("recognition run failed", e);
        }
    }
}
