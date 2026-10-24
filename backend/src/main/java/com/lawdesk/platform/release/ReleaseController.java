package com.lawdesk.platform.release;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets signed-in users, and so independent QA, check which backend commit answers them. The proxies never forward
 * {@code /actuator/info}; this answers the release id only, nothing about the configuration.
 */
@RestController
class ReleaseController {

    record ReleaseResponse(String release) {
    }

    private final RunningRelease running;

    ReleaseController(RunningRelease running) {
        this.running = running;
    }

    @GetMapping("/api/v1/release")
    @PreAuthorize("isAuthenticated()")
    ReleaseResponse release() {
        return new ReleaseResponse(running.id());
    }
}
