package com.lawdesk.platform.release;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * The release this process runs: the deployed commit ({@code lawdesk.release}, set from {@code LAWDESK_RELEASE} by the
 * staging compose file), falling back to the build version when the application runs outside a deployment.
 */
@Component
class RunningRelease {

    private final String id;
    private final String version;

    RunningRelease(ObjectProvider<BuildProperties> build, @Value("${lawdesk.release:}") String release) {
        BuildProperties properties = build.getIfAvailable();
        this.version = properties != null ? properties.getVersion() : "unknown";
        this.id = release.isBlank() ? version : release.strip();
    }

    String id() {
        return id;
    }

    String version() {
        return version;
    }
}
