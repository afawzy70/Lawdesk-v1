package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.documents.DocumentArchive;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The deadline register (LD-054, requirements section 10, owner decision D6). A deadline is MANUAL (typed by a user),
 * PROPOSED (computed from an approved rule) or APPROVED (by a competent member, whose name and time are kept). The system
 * never completes, misses or closes a deadline: only a user does. Every operation goes through the case-access policy (404
 * for a case the user cannot see, 403 for one they cannot change) and adds an entry to the case timeline in the same
 * transaction; every list is restricted to the cases the user sees.
 */
@Service
class DeadlineService {

    /** "Today" is the calendar date in Egypt, as for hearings and information requests. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private final DeadlineStore store;
    private final DeadlineSettingsStore settings;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final DocumentArchive documents;
    private final AuditLog audit;

    DeadlineService(DeadlineStore store, DeadlineSettingsStore settings, CaseLookup cases, CaseAccessPolicy access,
            CurrentActor currentActor, DocumentArchive documents, AuditLog audit) {
        this.store = store;
        this.settings = settings;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.documents = documents;
        this.audit = audit;
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static PageResponse<DeadlineRequests.Response> pageOf(List<DeadlineRequests.Response> items, int page, int size,
            long total) {
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    // ---- reads -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<DeadlineRequests.Response> forCase(UUID caseId, DeadlineStore.Filter filter, int page, int size) {
        access.requireView(caseId);
        DeadlineStore.Filter scoped = new DeadlineStore.Filter(filter.status(), filter.calcStatus(), filter.ownerId(),
                filter.from(), filter.to(), filter.overdue(), caseId, filter.sourceType(), filter.sourceId());
        return register(scoped, page, size);
    }

    @Transactional(readOnly = true)
    PageResponse<DeadlineRequests.Response> list(DeadlineStore.Filter filter, int page, int size) {
        return register(filter, page, size);
    }

    private PageResponse<DeadlineRequests.Response> register(DeadlineStore.Filter filter, int page, int size) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("d.case_id");
        LocalDate today = today();
        return pageOf(store.list(filter, visible, today, size, (long) page * size), page, size,
                store.count(filter, visible, today));
    }

    @Transactional(readOnly = true)
    DeadlineRequests.Response get(UUID id) {
        DeadlineStore.Head head = visibleHead(id, false);
        return one(head.id());
    }

    /** The deadlines that run from one judgment (its appeal, cassation or grievance deadlines, for example). */
    @Transactional(readOnly = true)
    PageResponse<DeadlineRequests.Response> forJudgment(UUID judgmentId, int page, int size) {
        UUID caseId = store.caseOfJudgment(judgmentId).orElseThrow(() -> new NotFoundException("judgment"));
        access.requireView(caseId);
        DeadlineStore.Filter filter = new DeadlineStore.Filter(null, null, null, null, null, null, caseId, "JUDGMENT",
                judgmentId);
        return register(filter, page, size);
    }

    // ---- writes ------------------------------------------------------------------------------------------------

    @Transactional
    DeadlineRequests.Response create(UUID caseId, DeadlineRequests.Create request) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = writableCase(caseId);
        Source source = source(caseId, request.sourceType(), request.sourceId(), request.sourceText());
        if (request.dueOn() == null && request.periodDays() == null) {
            throw new FieldErrorException("dueOn", "NotNull");
        }
        requireOrder(request.startsOn(), request.dueOn());
        requireUser("ownerId", request.ownerId());
        UUID id = store.insert(new DeadlineStore.NewDeadline(caseId, request.sourceType().name(), source.id(),
                source.text(), source.label(), request.startsOn(), request.periodDays(), request.dueOn(), null, null,
                request.action(), request.ownerId(), request.notes(), "MANUAL"), actor.id());
        cases.recordEvent(legalCase.id(), "DEADLINE_CREATED", "Deadline recorded (awaiting approval): %s%s"
                .formatted(brief(request.action()), request.dueOn() == null ? "" : ", due " + request.dueOn()));
        return one(id);
    }

    @Transactional
    DeadlineRequests.Response propose(UUID caseId, DeadlineRequests.Propose request) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = writableCase(caseId);
        Source source = source(caseId, request.sourceType(), request.sourceId(), request.sourceText());
        requireUser("ownerId", request.ownerId());
        DeadlineRequests.RuleResponse rule = approvedRule(request.ruleId());
        if (request.startsOn().isBefore(rule.effectiveFrom())) {
            throw new BusinessRuleException(Rules.RULE_NOT_EFFECTIVE,
                    "the rule is effective from " + rule.effectiveFrom());
        }
        DeadlineCalculation calculation = DeadlineCalculator.calculate(new DeadlineCalculator.Rule(rule.id(), rule.code(),
                rule.label(), rule.periodDays(), rule.countMethod(), rule.countsStartDay(), rule.rollForward(),
                rule.closedWeekdays(), rule.legalSource(), rule.effectiveFrom()), request.startsOn(),
                settings.approvedHolidays());
        UUID id = store.insert(new DeadlineStore.NewDeadline(caseId, request.sourceType().name(), source.id(),
                source.text(), source.label(), request.startsOn(), rule.periodDays(), calculation.dueOn(), rule.id(),
                calculation, request.action(), request.ownerId(), request.notes(), "PROPOSED"), actor.id());
        cases.recordEvent(legalCase.id(), "DEADLINE_PROPOSED", "Deadline proposed from rule %s (awaiting approval): %s, due %s"
                .formatted(rule.code(), brief(request.action()), calculation.dueOn()));
        return one(id);
    }

    @Transactional
    DeadlineRequests.Response update(UUID id, DeadlineRequests.Update request) {
        ActorRef actor = currentActor.require();
        DeadlineStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        requireVersion(head, request.version());
        if (!head.status().equals("OPEN")) {
            throw new BusinessRuleException(Rules.DEADLINE_NOT_OPEN, "only an open deadline can be edited");
        }
        if (request.dueOn() == null && request.periodDays() == null) {
            throw new FieldErrorException("dueOn", "NotNull");
        }
        requireOrder(request.startsOn(), request.dueOn());
        requireUser("ownerId", request.ownerId());
        boolean datesChanged = !Objects.equals(head.startsOn(), request.startsOn())
                || !Objects.equals(head.periodDays(), request.periodDays())
                || !Objects.equals(head.dueOn(), request.dueOn());
        String calcStatus = head.calcStatus();
        boolean keepCalculation = true;
        if (datesChanged && !calcStatus.equals("MANUAL")) {
            // A proposed or approved date edited by hand is a manual date again: it needs a reason and a new approval.
            if (request.changeReason() == null) {
                throw new BusinessRuleException(Rules.DEADLINE_CHANGE_REASON_REQUIRED,
                        "say why the dates of a proposed or approved deadline are changed");
            }
            calcStatus = "MANUAL";
            keepCalculation = false;
        }
        if (!store.update(id, request.version(), request.startsOn(), request.periodDays(), request.dueOn(),
                request.action(), request.ownerId(), request.notes(), calcStatus, keepCalculation,
                calcStatus.equals("APPROVED"), request.changeReason(), actor.id())) {
            throw new ConflictException("deadline was changed by someone else");
        }
        cases.recordEvent(head.caseId(), "DEADLINE_UPDATED", "Deadline updated: %s%s".formatted(brief(request.action()),
                datesChanged ? ", due " + (head.dueOn() == null ? "not set" : head.dueOn()) + " -> "
                        + (request.dueOn() == null ? "not set" : request.dueOn()) : ""));
        return one(id);
    }

    @Transactional
    DeadlineRequests.Response approve(UUID id, long version) {
        ActorRef actor = currentActor.require();
        DeadlineStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        requireVersion(head, version);
        if (head.calcStatus().equals("APPROVED")) {
            throw new BusinessRuleException(Rules.DEADLINE_ALREADY_APPROVED, "the deadline is already approved");
        }
        if (head.dueOn() == null) {
            throw new BusinessRuleException(Rules.DEADLINE_NO_DUE_DATE, "a deadline without a due date cannot be approved");
        }
        if (!store.approve(id, version, actor.id())) {
            throw new ConflictException("deadline was changed by someone else");
        }
        audit.recordWithChange("DEADLINE_APPROVE", actor.id(), actor.username(), "DEADLINE", id.toString(),
                head.calcStatus());
        cases.recordEvent(head.caseId(), "DEADLINE_APPROVED", "Deadline approved: %s, due %s"
                .formatted(brief(head.action()), head.dueOn()));
        return one(id);
    }

    @Transactional
    DeadlineRequests.Response changeStatus(UUID id, DeadlineRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        DeadlineStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        requireVersion(head, request.version());
        DeadlineRequests.Status target = request.status();
        boolean fromOpen = head.status().equals("OPEN");
        if (fromOpen == (target == DeadlineRequests.Status.OPEN)) {
            throw new BusinessRuleException(Rules.DEADLINE_TRANSITION_NOT_ALLOWED,
                    head.status() + " cannot move to " + target);
        }
        LocalDate completedOn = null;
        UUID proof = null;
        if (target != DeadlineRequests.Status.OPEN && head.calcStatus().equals("PROPOSED")) {
            throw new BusinessRuleException(Rules.DEADLINE_PROPOSAL_PENDING,
                    "a proposed deadline must be approved (or changed by hand) first");
        }
        if (target == DeadlineRequests.Status.COMPLETED) {
            completedOn = request.completedOn() == null ? today() : request.completedOn();
            if (completedOn.isAfter(today())) {
                throw new BusinessRuleException(Rules.COMPLETION_IN_FUTURE, "the completion date is in the future");
            }
            proof = request.proofDocumentId();
            if (proof != null && !documents.belongsToCase(proof, head.caseId())) {
                throw new BusinessRuleException(Rules.PROOF_NOT_IN_CASE, "the document is not in this case file");
            }
        } else if (request.completedOn() != null || request.proofDocumentId() != null) {
            throw new FieldErrorException("completedOn", "Null");
        }
        if (!store.setStatus(id, request.version(), target.name(), completedOn, proof, actor.id())) {
            throw new ConflictException("deadline was changed by someone else");
        }
        cases.recordEvent(head.caseId(), "DEADLINE_" + (target == DeadlineRequests.Status.OPEN ? "REOPENED" : target.name()),
                "Deadline %s: %s".formatted(target == DeadlineRequests.Status.OPEN ? "reopened"
                        : target.name().toLowerCase(java.util.Locale.ROOT), brief(head.action())));
        return one(id);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /** What a deadline runs from: the id and text as stored, and the neutral label read from the record. */
    private record Source(UUID id, String text, String label) {
    }

    private Source source(UUID caseId, DeadlineRequests.SourceType type, UUID sourceId, String sourceText) {
        if (type == DeadlineRequests.SourceType.TEXT) {
            if (sourceId != null) {
                throw new FieldErrorException("sourceId", "Null");
            }
            if (sourceText == null) {
                throw new FieldErrorException("sourceText", "NotNull");
            }
            return new Source(null, sourceText, null);
        }
        if (sourceId == null) {
            throw new FieldErrorException("sourceId", "NotNull");
        }
        if (sourceText != null) {
            throw new FieldErrorException("sourceText", "Null");
        }
        String label = store.sourceLabel(type.name(), sourceId, caseId)
                .orElseThrow(() -> new BusinessRuleException(Rules.SOURCE_NOT_IN_CASE, "the source is not part of this case"));
        return new Source(sourceId, null, label);
    }

    /** The rule to compute from: it must exist and be approved, otherwise nothing is proposed. */
    private DeadlineRequests.RuleResponse approvedRule(UUID ruleId) {
        if (ruleId == null) {
            if (!settings.approvedRuleExists()) {
                throw new BusinessRuleException(Rules.NO_APPROVED_RULE, "no approved deadline rule exists");
            }
            throw new FieldErrorException("ruleId", "NotNull");
        }
        return settings.rule(ruleId).filter(rule -> rule.status().equals("APPROVED"))
                .orElseThrow(() -> new BusinessRuleException(Rules.NO_APPROVED_RULE, "the rule is not approved"));
    }

    private DeadlineStore.Head visibleHead(UUID id, boolean lock) {
        DeadlineStore.Head head = store.head(id, false).orElseThrow(() -> new NotFoundException("deadline"));
        access.requireView(head.caseId());
        return lock ? store.head(id, true).orElseThrow(() -> new NotFoundException("deadline")) : head;
    }

    private DeadlineRequests.Response one(UUID id) {
        return store.find(id, today()).orElseThrow(() -> new NotFoundException("deadline"));
    }

    private static void requireVersion(DeadlineStore.Head head, long version) {
        if (head.version() != version) {
            throw new ConflictException("deadline was changed by someone else");
        }
    }

    private static void requireOrder(LocalDate startsOn, LocalDate dueOn) {
        if (startsOn != null && dueOn != null && dueOn.isBefore(startsOn)) {
            throw new FieldErrorException("dueOn", "Range");
        }
    }

    private void requireUser(String field, UUID userId) {
        if (userId != null && !store.userExists(userId)) {
            throw new FieldErrorException(field, "Invalid");
        }
    }

    /** The case, readable and changeable by the signed-in user; an archived case takes no new records. */
    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private static String brief(String text) {
        return text.length() <= 160 ? text : text.substring(0, 160) + "...";
    }
}
