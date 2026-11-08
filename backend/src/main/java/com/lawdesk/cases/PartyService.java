package com.lawdesk.cases;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Legal parties and their links to cases (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-016). */
@Service
class PartyService {

    static final String PARTY_KIND_MISMATCH = "PARTY_KIND_MISMATCH";
    static final String PARTY_IS_AUTHORITY = "PARTY_IS_AUTHORITY";

    private final PartyRepository parties;
    private final CaseRepository cases;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;

    PartyService(PartyRepository parties, CaseRepository cases, CaseTimeline timeline, CurrentActor currentActor,
            CaseAccessPolicy access) {
        this.access = access;
        this.parties = parties;
        this.cases = cases;
        this.timeline = timeline;
        this.currentActor = currentActor;
    }

    @Transactional
    Party create(PartyRequest request) {
        ActorRef actor = currentActor.require();
        check(request, null);
        try {
            return parties.insert(request, actor.id());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("national id already used");
        }
    }

    @Transactional
    Party update(UUID id, PartyRequest request) {
        ActorRef actor = currentActor.require();
        if (!parties.exists(id)) {
            throw new NotFoundException("party");
        }
        check(request, id);
        try {
            return parties.update(id, request, actor.id())
                    .orElseThrow(() -> new ConflictException("party " + id + " has changed"));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("national id already used");
        }
    }

    @Transactional(readOnly = true)
    Party get(UUID id) {
        return parties.find(id).orElseThrow(() -> new NotFoundException("party"));
    }

    @Transactional(readOnly = true)
    PageResponse<Party> search(Paging paging) {
        return parties.search(paging);
    }

    @Transactional(readOnly = true)
    List<CaseParty> ofCase(UUID caseId) {
        access.requireView(caseId);
        return parties.ofCase(caseId);
    }

    @Transactional
    CaseParty link(UUID caseId, LinkPartyRequest request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        LegalCase legalCase = requireChangeableParties(caseId);
        Party party = parties.find(request.partyId()).orElseThrow(() -> new NotFoundException("party"));
        if (party.authority()) {
            throw new BusinessRuleException(PARTY_IS_AUTHORITY, "the authority is part of the case already");
        }
        PartyRole role = PartyRole.valueOf(request.role());
        Side side = request.side() == null ? null : Side.valueOf(request.side());
        if (legalCase.authorityPosition() != null) {
            // A case registered through the intake: the party's capacity is its side, and the role follows from it.
            if (side == null) {
                throw new FieldErrorException("side", "NotNull");
            }
            role = side == legalCase.authorityPosition() ? PartyRole.OTHER : PartyRole.OPPONENT;
        }
        if (parties.linked(caseId, request.partyId()).isPresent()) {
            throw new ConflictException("party is already on the case");
        }
        try {
            parties.link(caseId, request.partyId(), role, side, actor.id());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("party is already on the case");
        }
        CaseParty linked = parties.linked(caseId, request.partyId()).orElseThrow();
        timeline.append(caseId, CaseTimeline.PARTY_ADDED, linked.name() + " added as "
                + (side == null ? role.name() : side.name()), actor.id());
        return linked;
    }

    @Transactional
    void unlink(UUID caseId, UUID partyId) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        requireChangeableParties(caseId);
        CaseParty current = parties.linked(caseId, partyId).orElseThrow(() -> new NotFoundException("case party"));
        if (current.authority()) {
            throw new BusinessRuleException(PARTY_IS_AUTHORITY, "the authority stays on its case");
        }
        CaseParty removed = parties.unlink(caseId, partyId).orElseThrow(() -> new NotFoundException("case party"));
        timeline.append(caseId, CaseTimeline.PARTY_REMOVED, removed.name() + " removed as "
                + (removed.side() == null ? removed.role() : removed.side()), actor.id());
    }

    /** Only an archived case keeps its parties as they are (acceptance criterion LD-016 4). */
    private LegalCase requireChangeableParties(UUID caseId) {
        LegalCase legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if (legalCase.status() == CaseStatus.ARCHIVED) {
            throw new BusinessRuleException(CaseService.CASE_READ_ONLY, "case " + caseId + " is archived");
        }
        return legalCase;
    }

    private void check(PartyRequest request, UUID self) {
        PartyKind kind = request.partyKind();
        if (kind == PartyKind.ORGANIZATION && request.nationalId() != null
                || kind == PartyKind.PERSON && request.registrationNo() != null) {
            throw new BusinessRuleException(PARTY_KIND_MISMATCH, "identifier does not match the kind " + kind);
        }
        if (request.nationalId() != null && parties.nationalIdTaken(request.nationalId(), self)) {
            throw new ConflictException("national id already used");
        }
    }
}
