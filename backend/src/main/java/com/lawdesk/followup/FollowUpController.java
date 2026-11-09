package com.lawdesk.followup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Runs the follow-up now (the scheduled run does the same every hour); a holder of the escalation permission only. */
@RestController
@RequestMapping("/api/v1/follow-up")
class FollowUpController {

    private final FollowUpRun run;

    FollowUpController(FollowUpRun run) {
        this.run = run;
    }

    @PostMapping("/run")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_ESCALATE)
    FollowUpRun.Result run() {
        return run.run();
    }
}
