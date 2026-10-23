package com.lawdesk.platform.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records the running release in {@code app_release} once the application is ready to serve. The release is the
 * deployed commit ({@code lawdesk.release}, set from {@code LAWDESK_RELEASE} by the staging compose file) and falls
 * back to the build version when the application runs outside a deployment.
 */
@Component
class ReleaseRecorder {

    private static final Logger log = LoggerFactory.getLogger(ReleaseRecorder.class);

    private final AppReleaseRepository releases;
    private final ObjectProvider<BuildProperties> build;
    private final String release;

    ReleaseRecorder(AppReleaseRepository releases, ObjectProvider<BuildProperties> build,
            @Value("${lawdesk.release:}") String release) {
        this.releases = releases;
        this.build = build;
        this.release = release.strip();
    }

    @EventListener(ApplicationReadyEvent.class)
    void recordRelease() {
        BuildProperties properties = build.getIfAvailable();
        String version = properties != null ? properties.getVersion() : "unknown";
        String id = release.isEmpty() ? version : release;
        releases.save(new AppRelease(id));
        // Not "service.version": ECS already writes a "service" object and the line would be dropped.
        log.atInfo().addKeyValue("lawdesk.release.id", id).addKeyValue("lawdesk.release.version", version)
                .log("release recorded");
    }
}
