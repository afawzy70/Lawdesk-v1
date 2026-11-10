package com.lawdesk.cases;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.users.UserDirectory;

/**
 * Registers a case through the government intake (LD-045) atomically: the case with its numbers and financial impact,
 * the authority and the other parties on their sides, the first-instance proceeding, and last the specialist and
 * alternate members — so the user who registers a case may assign it to someone else without losing the right to finish
 * registering it. Anything that fails rolls the whole registration back.
 */
@Service
class IntakeService {

    private final CaseService caseService;
    private final CaseRepository cases;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final CaseTimeline timeline;
    private final FirstInstanceRecorder firstInstance;
    private final UserDirectory users;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final String authorityName;
    private final DuplicateCheck duplicates;

    static final String DUPLICATE_SUSPECTED = "DUPLICATE_SUSPECTED";

    IntakeService(CaseService caseService, CaseRepository cases, PartyRepository parties, PartyService partyService,
            CaseTimeline timeline, FirstInstanceRecorder firstInstance, UserDirectory users, CurrentActor currentActor,
            AuditLog audit, @Value("${lawdesk.authority.name:الهيئة}") String authorityName,
            DuplicateCheck duplicates) {
        this.duplicates = duplicates;
        this.caseService = caseService;
        this.cases = cases;
        this.parties = parties;
        this.partyService = partyService;
        this.timeline = timeline;
        this.firstInstance = firstInstance;
        this.users = users;
        this.currentActor = currentActor;
        this.audit = audit;
        this.authorityName = authorityName;
    }

    @Transactional
    LegalCase register(IntakeRequest request) {
        ActorRef actor = currentActor.require();
        if (!request.parties().isEmpty() && !currentActor.has(Permission.PARTY_MANAGE)) {
            throw new AccessDeniedException("parties need PARTY_MANAGE");
        }
        FinancialImpact impact = FinancialImpact.valueOf(request.financialImpact());
        if (impact == FinancialImpact.YES && request.financialAmount() == null) {
            throw new FieldErrorException("financialAmount", "NotNull");
        }
        if (impact == FinancialImpact.NO && request.financialAmount() != null) {
            throw new FieldErrorException("financialAmount", "Null");
        }
        if (request.primaryMemberId().equals(request.alternateMemberId())) {
            throw new FieldErrorException("alternateMemberId", "SameAsPrimary");
        }
        requireEligible(request.primaryMemberId(), "primaryMemberId");
        if (request.alternateMemberId() != null) {
            requireEligible(request.alternateMemberId(), "alternateMemberId");
        }
        Side position = Side.valueOf(request.authorityPosition());
        checkParties(request.parties());
        IntakeRequest.FirstInstance numbers = request.firstInstance();
        List<DuplicateCheck.Match> matches = duplicates.matches(new DuplicateCheck.Candidate(request.automatedNo(),
                numbers.courtId(), numbers.courtCaseNumber(), numbers.courtCaseYear(), request.title()));
        if (!matches.isEmpty() && !Boolean.TRUE.equals(request.confirmDistinct())) {
            throw new BusinessRuleException(DUPLICATE_SUSPECTED, matches.size() + " similar case(s) exist");
        }

        String referenceNo = caseService.generateReferenceNo();
        LegalCase created;
        try {
            created = cases.insertIntake(referenceNo, request.automatedNo(), request.title(),
                    CaseType.valueOf(request.caseType()), request.subject(), request.openedOn(), position, impact,
                    impact == FinancialImpact.YES ? request.financialAmount() : null, actor.id());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("reference number " + referenceNo + " is already used");
        }
        UUID caseId = created.id();
        timeline.append(caseId, CaseTimeline.CASE_CREATED, "Case " + referenceNo + " created", actor.id());

        UUID authority = parties.authorityParty(authorityName, actor.id());
        parties.link(caseId, authority, PartyRole.CLIENT, position, actor.id());
        for (IntakeRequest.IntakeParty entry : request.parties()) {
            link(caseId, entry, position, actor);
        }

        IntakeRequest.FirstInstance stage = request.firstInstance();
        firstInstance.record(caseId, stage.courtId(), stage.courtCaseNumber(), stage.courtCaseYear(),
                stage.filingDate());

        caseService.checkDetails(request.details());
        cases.setDetails(caseId, request.details());
        for (DuplicateCheck.Match match : matches) {
            cases.recordDuplicateDecision(caseId, match, request.distinctNote(), actor.id());
        }
        if (!matches.isEmpty()) {
            timeline.append(caseId, "DUPLICATE_REVIEWED", CaseTimeline.fit("Registered as a different case from "
                    + matches.size() + " similar one(s)"), actor.id());
        }
        cases.assignNew(caseId, request.primaryMemberId(), request.alternateMemberId());
        LegalCase registered = cases.find(caseId).orElseThrow();
        caseService.recordAssignment(registered, actor);
        audit.recordWithChange("CASE_CREATE", actor.id(), actor.username(), "CASE", caseId.toString(), "intake");
        return registered;
    }

    private void link(UUID caseId, IntakeRequest.IntakeParty entry, Side position, ActorRef actor) {
        Side side = Side.valueOf(entry.side());
        UUID partyId;
        if (entry.partyId() != null) {
            Party existing = partyService.requireListed(entry.partyId());
            if (existing.authority()) {
                throw new BusinessRuleException(PartyService.PARTY_IS_AUTHORITY, "the authority is on the case already");
            }
            partyId = existing.id();
        } else {
            partyId = partyService.create(entry.newParty()).id();
        }
        PartyRole role = side == position ? PartyRole.OTHER : PartyRole.OPPONENT;
        parties.link(caseId, partyId, role, side, actor.id());
        CaseParty linked = parties.linked(caseId, partyId).orElseThrow();
        timeline.append(caseId, CaseTimeline.PARTY_ADDED, linked.name() + " added as " + side, actor.id());
    }

    /** Each entry names a registered party or gives a new one, and a registered party appears once. */
    private static void checkParties(List<IntakeRequest.IntakeParty> entries) {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            IntakeRequest.IntakeParty entry = entries.get(i);
            if ((entry.partyId() == null) == (entry.newParty() == null)) {
                throw new FieldErrorException("parties[" + i + "].partyId", "OneOf");
            }
            if (entry.partyId() != null && !seen.add(entry.partyId())) {
                throw new FieldErrorException("parties[" + i + "].partyId", "Duplicate");
            }
        }
    }

    private void requireEligible(UUID member, String field) {
        if (!users.isLegalDepartmentMember(member)) {
            throw new BusinessRuleException(CaseService.MEMBER_NOT_ELIGIBLE,
                    field + " is not an active legal department member");
        }
    }
}
