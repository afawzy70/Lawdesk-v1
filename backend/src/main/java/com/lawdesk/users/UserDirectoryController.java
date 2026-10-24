package com.lawdesk.users;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The assignable users for task assignment and workload filters (architecture/CONTRACTS.md §8). */
@RestController
class UserDirectoryController {

    static final String READ = "hasAnyAuthority('TASK_VIEW', 'TASK_MANAGE', 'REPORT_VIEW')";

    private final UserDirectory directory;

    UserDirectoryController(UserDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/api/v1/users/directory")
    @PreAuthorize(READ)
    List<UserDirectory.UserSummary> active() {
        return directory.active();
    }
}
