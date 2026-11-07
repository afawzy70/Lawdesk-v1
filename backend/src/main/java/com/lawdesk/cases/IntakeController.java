package com.lawdesk.cases;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.users.UserDirectory;

/** The government intake and the case assignment (LD-045). */
@RestController
class IntakeController {

    private final IntakeService intake;
    private final CaseService cases;
    private final UserDirectory users;

    IntakeController(IntakeService intake, CaseService cases, UserDirectory users) {
        this.intake = intake;
        this.cases = cases;
        this.users = users;
    }

    @PostMapping("/api/v1/cases/intake")
    @PreAuthorize("hasAuthority('CASE_CREATE') and hasAuthority('PROCEEDING_MANAGE')")
    ResponseEntity<LegalCase> register(@Valid @RequestBody IntakeRequest request) {
        LegalCase created = intake.register(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/cases/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /** The active legal department members who can be assigned (العضو المختص / العضو البديل). */
    @GetMapping("/api/v1/cases/members")
    @PreAuthorize("hasAnyAuthority('CASE_CREATE', 'CASE_EDIT')")
    List<UserDirectory.UserSummary> members() {
        return users.legalDepartmentMembers();
    }

    /** Reassignment is for case supervisors; the members themselves cannot hand a case over. */
    @PutMapping("/api/v1/cases/{id}/assignment")
    @PreAuthorize("hasAuthority('CASE_EDIT') and hasAuthority('CASE_STATUS_CHANGE')")
    LegalCase assign(@PathVariable String id, @Valid @RequestBody AssignmentRequest request) {
        return cases.assign(Text.id(id, "case"), request);
    }
}
