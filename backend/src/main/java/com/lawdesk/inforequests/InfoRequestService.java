package com.lawdesk.inforequests;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.documents.DocumentArchive;
import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Requests for statements and documents addressed to the authority's units (LD-053, requirements section 18). Every
 * write goes through the case-access policy (404 for a case the user cannot see, 403 for one they cannot change) and adds
 * an entry to the case timeline in the same transaction; every list is restricted to the cases the user sees.
 */
@Service
class InfoRequestService {

    /** "Today" is the calendar date in Kuwait (Asia/Kuwait), as for hearings (ASSUMPTION of the hearing module, common rule 9). */
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuwait");

    /** Which statuses a user may move a request to, from each status. A late request is only moved by its replies. */
    private static final Map<InfoRequestStatus, Set<InfoRequestStatus>> TRANSITIONS = Map.of(
            InfoRequestStatus.DRAFT, Set.of(InfoRequestStatus.PENDING_APPROVAL, InfoRequestStatus.SENT, InfoRequestStatus.CLOSED),
            InfoRequestStatus.PENDING_APPROVAL, Set.of(InfoRequestStatus.DRAFT, InfoRequestStatus.SENT, InfoRequestStatus.CLOSED),
            InfoRequestStatus.SENT, Set.of(InfoRequestStatus.AWAITING, InfoRequestStatus.CLOSED),
            InfoRequestStatus.AWAITING, Set.of(InfoRequestStatus.CLOSED),
            InfoRequestStatus.OVERDUE, Set.of(InfoRequestStatus.CLOSED),
            InfoRequestStatus.PARTIAL, Set.of(InfoRequestStatus.AWAITING, InfoRequestStatus.CLOSED),
            InfoRequestStatus.INCOMPLETE, Set.of(InfoRequestStatus.AWAITING, InfoRequestStatus.CLOSED),
            InfoRequestStatus.COMPLETE, Set.of(InfoRequestStatus.CLOSED),
            InfoRequestStatus.CLOSED, Set.of(InfoRequestStatus.AWAITING));

    private final InfoRequestStore store;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final DocumentArchive documents;
    private final NotificationPublisher notifications;

    InfoRequestService(InfoRequestStore store, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            DocumentArchive documents, NotificationPublisher notifications) {
        this.store = store;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.documents = documents;
        this.notifications = notifications;
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // ---- reads -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<InfoRequestRequests.Response> forCase(UUID caseId, int page, int size) {
        access.requireView(caseId);
        List<InfoRequestRequests.Response> rows = store.forCase(caseId, today(), size, (long) page * size);
        return pageOf(withChildren(rows), page, size, store.countForCase(caseId));
    }

    @Transactional(readOnly = true)
    InfoRequestRequests.Response get(UUID id) {
        InfoRequestStore.Head head = visibleHead(id, false);
        return one(head.id());
    }

    @Transactional(readOnly = true)
    PageResponse<InfoRequestRequests.Response> list(String status, Boolean overdue, UUID unitId, int page, int size) {
        InfoRequestStore.Filter filter = new InfoRequestStore.Filter(status, overdue, unitId, null);
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("r.case_id");
        List<InfoRequestRequests.Response> rows = store.list(filter, visible, today(), size, (long) page * size);
        return pageOf(rows, page, size, store.count(filter, visible, today()));
    }

    @Transactional(readOnly = true)
    InfoRequestRequests.Report report() {
        return new InfoRequestRequests.Report(store.report(access.visibleCaseIds("r.case_id"), today()));
    }

    // ---- writes ------------------------------------------------------------------------------------------------

    @Transactional
    InfoRequestRequests.Response create(UUID caseId, InfoRequestRequests.Create request) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = writableCase(caseId);
        String unitName = unitName(request.unitId());
        requireUser("responsibleMemberId", request.responsibleMemberId());
        UUID id = store.insert(caseId, request, unitName, actor.id());
        InfoRequestRequests.Response created = one(id);
        cases.recordEvent(legalCase.id(), "INFO_REQUEST_CREATED", "Information request %s created: %s"
                .formatted(created.requestNo(), request.subject()));
        return created;
    }

    @Transactional
    InfoRequestRequests.Response update(UUID id, InfoRequestRequests.Update request) {
        ActorRef actor = currentActor.require();
        InfoRequestStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        if (head.version() != request.version()) {
            throw new ConflictException("information request was changed by someone else");
        }
        String unitName = unitName(request.unitId());
        requireUser("responsibleMemberId", request.responsibleMemberId());
        // An overdue request whose due date moved forward is waiting again, in the state it was in before.
        InfoRequestStatus status = head.status();
        InfoRequestStatus before = head.statusBeforeOverdue();
        if (status == InfoRequestStatus.OVERDUE && (request.dueOn() == null || !request.dueOn().isBefore(today()))) {
            status = before == null ? InfoRequestStatus.AWAITING : before;
            before = null;
        }
        if (!store.update(id, request, unitName, status, before, actor.id())) {
            throw new ConflictException("information request was changed by someone else");
        }
        cases.recordEvent(head.caseId(), "INFO_REQUEST_UPDATED", "Information request %s updated"
                .formatted(head.requestNo()));
        return one(id);
    }

    @Transactional
    InfoRequestRequests.Response changeStatus(UUID id, InfoRequestRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        InfoRequestStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        InfoRequestStatus target = request.status();
        if (!InfoRequestStatus.SETTABLE.contains(target)) {
            throw new FieldErrorException("status", "Pattern");
        }
        if (head.version() != request.version()) {
            throw new ConflictException("information request was changed by someone else");
        }
        if (!TRANSITIONS.getOrDefault(head.status(), Set.of()).contains(target)) {
            throw new BusinessRuleException(Rules.INFO_REQUEST_TRANSITION_NOT_ALLOWED,
                    head.status() + " cannot move to " + target);
        }
        if (target == InfoRequestStatus.CLOSED && head.status() != InfoRequestStatus.COMPLETE
                && request.closeReason() == null) {
            throw new FieldErrorException("closeReason", "NotNull");
        }
        LocalDate today = today();
        LocalDate sentOn = target == InfoRequestStatus.SENT ? (request.sentOn() == null ? today : request.sentOn()) : null;
        LocalDate closedOn = target == InfoRequestStatus.CLOSED ? today : null;
        String closeReason = target == InfoRequestStatus.CLOSED ? request.closeReason() : null;
        if (!store.changeStatus(id, request.version(), target, sentOn, request.letterNo(), request.letterDate(),
                closeReason, closedOn, actor.id())) {
            throw new ConflictException("information request was changed by someone else");
        }
        cases.recordEvent(head.caseId(), "INFO_REQUEST_" + target.name(), "Information request %s is now %s"
                .formatted(head.requestNo(), target.name()));
        return one(id);
    }

    @Transactional
    InfoRequestRequests.Reply reply(UUID id, InfoRequestRequests.ReplyInput input, MultipartFile file, String title) {
        ActorRef actor = currentActor.require();
        InfoRequestStore.Head head = visibleHead(id, true);
        writableCase(head.caseId());
        requireSent(head);
        if (input.receivedOn().isAfter(today())) {
            throw new FieldErrorException("receivedOn", "Range");
        }
        UUID documentId = input.documentId();
        if (file != null) {
            documentId = documents.archive(head.caseId(), file, title == null ? "Reply to " + head.requestNo() : title);
        } else if (documentId != null && !documents.belongsToCase(documentId, head.caseId())) {
            throw new BusinessRuleException(Rules.REPLY_DOCUMENT_NOT_IN_CASE, "the document is not in this case file");
        }
        UUID replyId = store.insertReply(id, head.caseId(), input, documentId, actor.id());
        store.statusFromReplies(id).ifPresent(status -> store.followReplies(id, status, actor.id()));
        cases.recordEvent(head.caseId(), "INFO_REPLY_RECEIVED", "Reply (%s) received for information request %s"
                .formatted(input.completeness().name().toLowerCase(java.util.Locale.ROOT), head.requestNo()));
        return store.reply(id, replyId).orElseThrow();
    }

    @Transactional
    InfoRequestRequests.FollowUp followUp(UUID id, InfoRequestRequests.FollowUpInput input) {
        ActorRef actor = currentActor.require();
        InfoRequestStore.Head head = visibleHead(id, true);
        CaseSummary legalCase = writableCase(head.caseId());
        boolean escalation = input.kind() == InfoRequestRequests.FollowUpKind.ESCALATION;
        if (escalation && !currentActor.has(Permission.INFO_REQUEST_ESCALATE)) {
            throw new AccessDeniedException("missing INFO_REQUEST_ESCALATE");
        }
        if (escalation == (input.escalatedTo() == null)) {
            throw new FieldErrorException("escalatedTo", escalation ? "NotNull" : "Null");
        }
        if (escalation) {
            requireUser("escalatedTo", input.escalatedTo());
        }
        requireSent(head);
        LocalDate onDate = input.onDate() == null ? today() : input.onDate();
        UUID followUpId = store.insertFollowUp(id, head.caseId(), input, onDate, actor.id());
        cases.recordEvent(head.caseId(), "INFO_REQUEST_" + input.kind().name(), "Information request %s: %s"
                .formatted(head.requestNo(), input.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
        if (escalation) {
            notifications.publish(input.escalatedTo(), NotificationPublisher.INFO_REQUEST_ESCALATED, legalCase.id(),
                    "INFO_REQUEST:" + id + ":" + followUpId,
                    Map.of("requestNo", head.requestNo(), "caseReferenceNo", legalCase.referenceNo()));
        }
        return store.followUp(id, followUpId).orElseThrow();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private InfoRequestStore.Head visibleHead(UUID id, boolean lock) {
        InfoRequestStore.Head head = store.head(id, false).orElseThrow(() -> new NotFoundException("information request"));
        access.requireView(head.caseId());
        return lock ? store.head(id, true).orElseThrow(() -> new NotFoundException("information request")) : head;
    }

    private InfoRequestRequests.Response one(UUID id) {
        InfoRequestRequests.Response row = store.find(id, today())
                .orElseThrow(() -> new NotFoundException("information request"));
        return withChildren(List.of(row)).get(0);
    }

    private List<InfoRequestRequests.Response> withChildren(List<InfoRequestRequests.Response> rows) {
        List<UUID> ids = rows.stream().map(InfoRequestRequests.Response::id).toList();
        Map<UUID, List<InfoRequestRequests.Reply>> replies = store.replies(ids);
        Map<UUID, List<InfoRequestRequests.FollowUp>> followUps = store.followUps(ids);
        return rows.stream().map(r -> r.withChildren(replies.getOrDefault(r.id(), List.of()),
                followUps.getOrDefault(r.id(), List.of()))).toList();
    }

    private static PageResponse<InfoRequestRequests.Response> pageOf(List<InfoRequestRequests.Response> items, int page,
            int size, long total) {
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
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

    private String unitName(UUID unitId) {
        if (unitId == null) {
            return null;
        }
        return store.authorityUnitPath(unitId).orElseThrow(() -> new FieldErrorException("unitId", "Invalid"));
    }

    private void requireUser(String field, UUID userId) {
        if (userId != null && !store.userExists(userId)) {
            throw new FieldErrorException(field, "Invalid");
        }
    }

    private static void requireSent(InfoRequestStore.Head head) {
        if (head.status() == InfoRequestStatus.CLOSED) {
            throw new BusinessRuleException(Rules.INFO_REQUEST_CLOSED, "the request is closed");
        }
        if (head.status() == InfoRequestStatus.DRAFT || head.status() == InfoRequestStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(Rules.INFO_REQUEST_NOT_SENT, "the request has not been sent yet");
        }
    }
}
