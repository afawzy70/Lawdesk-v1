package com.lawdesk.platform.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records the running build in {@code app_release} once the application is ready to serve. */
@Component
class ReleaseRecorder {

    private static final Logger log = LoggerFactory.getLogger(ReleaseRecorder.class);

    private final AppReleaseRepository releases;
    private final ObjectProvider<BuildProperties> build;

    ReleaseRecorder(AppReleaseRepository releases, ObjectProvider<BuildProperties> build) {
        this.releases = releases;
        this.build = build;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recordRelease() {
        BuildProperties properties = build.getIfAvailable();
        String version = properties != null ? properties.getVersion() : "unknown";
        releases.save(new AppRelease(version));
        // Not "service.version": ECS already writes a "service" object and the line would be dropped.
        log.atInfo().addKeyValue("lawdesk.release.version", version).log("release recorded");
    }
}
