package com.lawdesk.assistant;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/**
 * The legal assistant, LD-059 (requirements section 22). It needs ASSISTANT_USE and, for what it reads, the same permissions and
 * the same case access as the screens it reads from. {@code /status} is open to every signed-in user: it says plainly that the
 * assistant is a non-production test and not operational, and carries no case data.
 */
@RestController
@RequestMapping("/api/v1/assistant")
class AssistantController {

    private final AssistantService service;
    private final AssistantStatus status;
    private final AssistantOutputs outputs;
    private final QueryLog log;

    AssistantController(AssistantService service, AssistantStatus status, AssistantOutputs outputs, QueryLog log) {
        this.service = service;
        this.status = status;
        this.outputs = outputs;
        this.log = log;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    AssistantStatus.Status status() {
        return status.current();
    }

    @PostMapping("/ask")
    @PreAuthorize(Permission.Codes.ASSISTANT_USE)
    AssistantRequests.Answer ask(@Valid @RequestBody AssistantRequests.Ask request) {
        return service.ask(request);
    }

    @GetMapping("/outputs")
    @PreAuthorize(Permission.Codes.ASSISTANT_USE)
    List<AssistantRequests.Output> outputs(@RequestParam(required = false) UUID caseId) {
        return outputs.mine(caseId);
    }

    @GetMapping("/outputs/{id}")
    @PreAuthorize(Permission.Codes.ASSISTANT_USE)
    AssistantRequests.Output output(@PathVariable UUID id) {
        return outputs.get(id);
    }

    @PostMapping("/outputs/{id}/review")
    @PreAuthorize(Permission.Codes.ASSISTANT_USE)
    ResponseEntity<AssistantRequests.Output> review(@PathVariable UUID id, @Valid @RequestBody AssistantRequests.Review review) {
        return ResponseEntity.ok(outputs.review(id, review));
    }

    /** The user's own queries; {@code scope=all} (AUDIT_VIEW) adds those about cases the reviewer may see. */
    @GetMapping("/queries")
    @PreAuthorize(Permission.Codes.ASSISTANT_USE)
    List<QueryLog.Entry> queries(@RequestParam(required = false) UUID caseId, @RequestParam(defaultValue = "mine") String scope) {
        return "all".equals(scope) ? log.reviewed(caseId) : log.mine(caseId);
    }
}
