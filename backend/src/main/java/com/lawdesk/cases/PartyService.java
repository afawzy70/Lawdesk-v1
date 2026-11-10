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
import com.lawdesk.security.Permission;

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
            return shown(parties.insert(request, actor.id()));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("national id already used");
        }
    }

    @Transactional
    Party update(UUID id, PartyRequest request) {
        ActorRef actor = currentActor.require();
        Party stored = requireListed(id);
        // D4: whoever may change a party but not read its identifiers cannot overwrite them with what they were shown.
        PartyRequest effective = canSeeIdentifiers() ? request : keepingIdentifiers(request, stored);
        check(effective, id);
        try {
            return shown(parties.update(id, effective, actor.id())
                    .orElseThrow(() -> new ConflictException("party " + id + " has changed")));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("national id already used");
        }
    }

    /**
     * The party, if the signed-in user may list it; otherwise 404, like a party that does not exist. A party is listed when it
     * is on at least one case the user may see, is on no case at all, was created by the user, or is the authority party. A
     * party that appears only on cases the user cannot see is therefore neither listed, read, edited nor linkable by them.
     */
    Party requireListed(UUID id) {
        return parties.findListed(id, access.visibleCaseIds("x.case_id"), currentActor.require().id())
                .orElseThrow(() -> new NotFoundException("party"));
    }

    @Transactional(readOnly = true)
    Party get(UUID id) {
        return shown(requireListed(id));
    }

    @Transactional(readOnly = true)
    PageResponse<Party> search(Paging paging) {
        PageResponse<Party> found = parties.search(paging, canSeeIdentifiers(), access.visibleCaseIds("x.case_id"), currentActor.require().id());
        return new PageResponse<>(found.items().stream().map(this::shown).toList(), found.page(), found.size(),
                found.totalItems(), found.totalPages());
    }

    /** The cases a party is on, among those the signed-in user may see (section 2: all the cases of one party). */
    @Transactional(readOnly = true)
    List<PartyRepository.PartyCase> casesOf(UUID id) {
        if (parties.findListed(id, access.visibleCaseIds("x.case_id"), currentActor.require().id()).isEmpty()) {
            throw new NotFoundException("party");
        }
        return parties.casesOf(id, access.viewable("c"));
    }

    private boolean canSeeIdentifiers() {
        return currentActor.has(Permission.PARTY_ID_VIEW);
    }

    /**
     * The party as the signed-in user may see it (owner decision D4): without PARTY_ID_VIEW the civil or establishment
     * number shows only its last characters, and a confidential party shows no contact details at all.
     */
    private Party shown(Party party) {
        if (canSeeIdentifiers()) {
            return party;
        }
        return new Party(party.id(), party.kind(), party.name(), mask(party.nationalId()), mask(party.registrationNo()),
                party.confidential() ? null : party.phone(), party.confidential() ? null : party.email(),
                party.confidential() ? null : party.address(), party.version(), party.createdAt(), party.createdBy(),
                party.updatedAt(), party.updatedBy(), party.authority(), party.confidential(), party.notes());
    }

    /** All but the last three characters hidden (and nothing shown of a value of three or fewer). */
    static String mask(String value) {
        if (value == null) {
            return null;
        }
        int shown = Math.max(0, Math.min(3, value.length() - 3));
        return "*".repeat(value.length() - shown) + value.substring(value.length() - shown);
    }

    private static PartyRequest keepingIdentifiers(PartyRequest request, Party stored) {
        return new PartyRequest(request.kind(), request.name(), stored.nationalId(), stored.registrationNo(),
                request.phone(), request.email(), request.address(), request.version(), request.confidential(),
                request.notes());
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
        Party party = requireListed(request.partyId());
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
        if (parties.hasCounsel(caseId, partyId)) {
            // case_party_counsel keeps its rows (removal is a mark) and references this link: refuse cleanly, not with a 500.
            throw new ConflictException("the party has a lawyer or agent recorded on the case; it cannot be removed from it");
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
