package com.lawdesk.platform.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records the {@link RunningRelease} in {@code app_release} once the application is ready to serve. */
@Component
class ReleaseRecorder {

    private static final Logger log = LoggerFactory.getLogger(ReleaseRecorder.class);

    private final AppReleaseRepository releases;
    private final RunningRelease running;

    ReleaseRecorder(AppReleaseRepository releases, RunningRelease running) {
        this.releases = releases;
        this.running = running;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recordRelease() {
        String id = running.id();
        String version = running.version();
        releases.save(new AppRelease(id));
        // Not "service.version": ECS already writes a "service" object and the line would be dropped.
        log.atInfo().addKeyValue("lawdesk.release.id", id).addKeyValue("lawdesk.release.version", version)
                .log("release recorded");
    }
}
