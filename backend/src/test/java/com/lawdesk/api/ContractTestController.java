package com.lawdesk.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/** Test-only endpoints exercising the shared API contract (architecture/CONTRACTS.md §3, §4). */
@RestController
@RequestMapping("/api/v1/contract-test")
class ContractTestController {

    private final CurrentActor currentActor;

    ContractTestController(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    record Body(@NotBlank @Size(max = 10) String name) {
    }

    @PostMapping("/validated")
    @PreAuthorize(Permission.Codes.CASE_CREATE)
    Body validated(@Valid @RequestBody Body body) {
        return body;
    }

    @GetMapping("/missing")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    void missing() {
        throw new NotFoundException("case");
    }

    @GetMapping("/conflict")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    void conflict() {
        throw new ConflictException("reference number already used");
    }

    @GetMapping("/stale")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    void stale() {
        throw new ObjectOptimisticLockingFailureException("LegalCase", "id");
    }

    @GetMapping("/rule")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    void rule() {
        throw new BusinessRuleException("APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT", "secret internal detail");
    }

    @GetMapping("/boom")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    void boom() {
        throw new IllegalStateException("secret internal detail");
    }

    @GetMapping("/me")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    ActorRef me() {
        return currentActor.require();
    }

    @GetMapping("/page")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<String> page(Pageable pageable) {
        return PageResponse.from(new PageImpl<>(List.of("a", "b"), pageable, 250));
    }
}
