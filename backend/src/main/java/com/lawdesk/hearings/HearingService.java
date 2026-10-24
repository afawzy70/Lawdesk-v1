package com.lawdesk.hearings;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.courts.Court;
import com.lawdesk.courts.CourtService;
import com.lawdesk.proceedings.ProceedingLookup;
import com.lawdesk.proceedings.ProceedingLookup.ProceedingSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Hearings of a proceeding (LD-024, docs/delivery/PHASE-5-ACCEPTANCE.md): scheduling, rescheduling, the result of a
 * hearing and the next hearing an adjournment creates. Cases, proceedings and courts are read through the ports of
 * Workstreams B and C; every recording write adds a case timeline entry in the same transaction.
 */
@Service
class HearingService {

    static final String HEARING_SCHEDULED = "HEARING_SCHEDULED";
    static final String HEARING_RESCHEDULED = "HEARING_RESCHEDULED";

    /** Hearings further ahead than this are taken for a mistyped year (ASSUMPTION). */
    private static final int YEARS_AHEAD = 2;
    /** The calendar shows the next 30 days unless asked otherwise, and at most 366 days at once (ASSUMPTION). */
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 366;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern(HearingRequests.TIME);

    private final HearingRepository hearings;
    private final CaseLookup cases;
    private final ProceedingLookup proceedings;
    private final CourtService courts;
    private final CurrentActor currentActor;

    HearingService(HearingRepository hearings, CaseLookup cases, ProceedingLookup proceedings, CourtService courts,
            CurrentActor currentActor) {
        this.hearings = hearings;
        this.cases = cases;
        this.proceedings = proceedings;
        this.courts = courts;
        this.currentActor = currentActor;
    }

    @Transactional
    HearingRequests.Response create(UUID proceedingId, HearingRequests.Create request) {
        ActorRef actor = currentActor.require();
        ProceedingSummary proceeding = proceedings.find(proceedingId)
                .orElseThrow(() -> new NotFoundException("proceeding"));
        CaseSummary legalCase = writableCase(proceeding.caseId());
        LocalDate today = LegalDay.today();
        requireWithinReach("hearingDate", request.hearingDate(), today);
        requireAfterFiling(proceeding, request.hearingDate());

        Hearing hearing = new Hearing(proceeding.caseId(), proceedingId, null, actor, LegalDay.now());
        hearing.plan(request.hearingDate(), request.hearingTime(), request.purpose(), request.notes());
        Hearing stored = store(hearing);
        cases.recordEvent(proceeding.caseId(), HEARING_SCHEDULED, "Hearing scheduled for %s (%s) in proceeding %s"
                .formatted(stored.getHearingDate(), stored.getPurpose(), numberOf(proceeding)));
        return new Lookups().respond(stored, legalCase, proceeding, null, today);
    }

    @Transactional(readOnly = true)
    HearingRequests.Response get(UUID id) {
        Hearing hearing = hearings.findById(id).orElseThrow(() -> new NotFoundException("hearing"));
        return new Lookups().respond(hearing, LegalDay.today());
    }

    @Transactional(readOnly = true)
    Page<HearingRequests.Response> forCase(UUID caseId, Pageable pageable) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        return respond(hearings.findForCase(caseId, unsorted(pageable)));
    }

    @Transactional(readOnly = true)
    Page<HearingRequests.Response> calendar(LocalDate from, LocalDate to, HearingStatus status, Pageable pageable) {
        LocalDate start = from == null ? LegalDay.today() : from;
        LocalDate end = to == null ? start.plusDays(DEFAULT_WINDOW_DAYS) : to;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_WINDOW_DAYS) {
            throw new FieldErrorException("to", "Range");
        }
        return respond(hearings.findBetween(start, end, status, unsorted(pageable)));
    }

    @Transactional
    HearingRequests.Response update(UUID id, HearingRequests.Update request) {
        ActorRef actor = currentActor.require();
        Hearing hearing = lockedCurrent(id, request.version());
        CaseSummary legalCase = writableCase(hearing.getCaseId());
        requireScheduled(hearing);
        LocalDate today = LegalDay.today();
        requireWithinReach("hearingDate", request.hearingDate(), today);
        ProceedingSummary proceeding = proceeding(hearing.getProceedingId());
        requireAfterFiling(proceeding, request.hearingDate());
        if (hearing.getAdjournedFromId() != null) {
            Hearing previous = hearings.findById(hearing.getAdjournedFromId()).orElseThrow();
            requireLater(previous, request.hearingDate());
        }

        boolean moved = !request.hearingDate().equals(hearing.getHearingDate())
                || !Objects.equals(request.hearingTime(), hearing.getHearingTime());
        hearing.plan(request.hearingDate(), request.hearingTime(), request.purpose(), request.notes());
        hearing.touch(actor, LegalDay.now());
        Hearing stored = store(hearing);
        if (moved) {
            cases.recordEvent(hearing.getCaseId(), HEARING_RESCHEDULED, "Hearing in proceeding %s moved to %s%s"
                    .formatted(numberOf(proceeding), stored.getHearingDate(), timeSuffix(stored.getHearingTime())));
        }
        Lookups lookups = new Lookups();
        return lookups.respond(stored, legalCase, proceeding, lookups.nextOf(stored.getId()), today);
    }

    @Transactional
    HearingRequests.Response recordResult(UUID id, HearingRequests.Result request) {
        ActorRef actor = currentActor.require();
        Hearing hearing = lockedCurrent(id, request.version());
        CaseSummary legalCase = writableCase(hearing.getCaseId());
        requireScheduled(hearing);
        HearingStatus outcome = request.status().hearingStatus();
        LocalDate today = LegalDay.today();
        if (outcome != HearingStatus.ADJOURNED) {
            refuseNextHearing(request);
        }
        if (outcome != HearingStatus.CANCELLED && hearing.getHearingDate().isAfter(today)) {
            throw new BusinessRuleException(Rules.HEARING_NOT_YET_HELD, "the hearing day has not come yet");
        }
        if (outcome == HearingStatus.ADJOURNED) {
            if (request.nextHearingDate() == null) {
                throw new FieldErrorException("nextHearingDate", "NotNull");
            }
            requireWithinReach("nextHearingDate", request.nextHearingDate(), today);
            requireLater(hearing, request.nextHearingDate());
        }

        hearing.recordResult(outcome, request.decision(), actor, LegalDay.now());
        Hearing stored = store(hearing);
        ProceedingSummary proceeding = proceeding(hearing.getProceedingId());
        String held = "Hearing of %s in proceeding %s".formatted(stored.getHearingDate(), numberOf(proceeding));
        UUID nextId = null;
        if (outcome == HearingStatus.ADJOURNED) {
            Hearing next = new Hearing(hearing.getCaseId(), hearing.getProceedingId(), hearing.getId(), actor,
                    LegalDay.now());
            next.plan(request.nextHearingDate(), request.nextHearingTime(),
                    request.nextHearingPurpose() == null ? hearing.getPurpose() : request.nextHearingPurpose(), null);
            nextId = store(next).getId();
            cases.recordEvent(hearing.getCaseId(), "HEARING_ADJOURNED", held + " adjourned to "
                    + request.nextHearingDate() + timeSuffix(request.nextHearingTime()));
        } else {
            cases.recordEvent(hearing.getCaseId(), "HEARING_" + outcome.name(), held + " "
                    + outcome.name().toLowerCase(Locale.ROOT));
        }
        return new Lookups().respond(stored, legalCase, proceeding, nextId, today);
    }

    private Hearing lockedCurrent(UUID id, long version) {
        Hearing hearing = hearings.lockById(id).orElseThrow(() -> new NotFoundException("hearing"));
        if (hearing.getVersion() != version) {
            throw new ConflictException("hearing was changed by someone else");
        }
        return hearing;
    }

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private ProceedingSummary proceeding(UUID id) {
        return proceedings.find(id).orElseThrow(() -> new NotFoundException("proceeding"));
    }

    private static void requireScheduled(Hearing hearing) {
        if (hearing.getStatus() != HearingStatus.SCHEDULED) {
            throw new BusinessRuleException(Rules.HEARING_NOT_SCHEDULED, "the hearing already has a result");
        }
    }

    private static void requireWithinReach(String field, LocalDate date, LocalDate today) {
        if (date.isAfter(today.plusYears(YEARS_AHEAD))) {
            throw new FieldErrorException(field, "Range");
        }
    }

    private static void requireAfterFiling(ProceedingSummary proceeding, LocalDate date) {
        if (date.isBefore(proceeding.filingDate())) {
            throw new BusinessRuleException(Rules.HEARING_BEFORE_FILING, "a hearing cannot precede the filing");
        }
    }

    private static void requireLater(Hearing previous, LocalDate date) {
        if (!date.isAfter(previous.getHearingDate())) {
            throw new BusinessRuleException(Rules.ADJOURNMENT_NOT_LATER, "the next hearing must follow the adjourned one");
        }
    }

    private static void refuseNextHearing(HearingRequests.Result request) {
        if (request.nextHearingDate() != null) {
            throw new FieldErrorException("nextHearingDate", "Null");
        }
        if (request.nextHearingTime() != null) {
            throw new FieldErrorException("nextHearingTime", "Null");
        }
        if (request.nextHearingPurpose() != null) {
            throw new FieldErrorException("nextHearingPurpose", "Null");
        }
    }

    /** Flushes now, so a hearing on a day already taken becomes a 409 instead of a failure at commit. */
    private Hearing store(Hearing hearing) {
        try {
            return hearings.saveAndFlush(hearing);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("the proceeding already has a hearing on that day");
        }
    }

    private Page<HearingRequests.Response> respond(Page<Hearing> page) {
        Lookups lookups = new Lookups();
        Map<UUID, UUID> next = hearings.findByAdjournedFromIdIn(page.map(Hearing::getId).getContent()).stream()
                .collect(Collectors.toMap(Hearing::getAdjournedFromId, Hearing::getId));
        LocalDate today = LegalDay.today();
        return page.map(h -> lookups.respond(h, lookups.legalCase(h.getCaseId()),
                lookups.proceeding(h.getProceedingId()), next.get(h.getId()), today));
    }

    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static String numberOf(ProceedingSummary proceeding) {
        return proceeding.courtCaseNumber() + "/" + proceeding.courtCaseYear();
    }

    private static String timeSuffix(LocalTime time) {
        return time == null ? "" : " " + time.format(TIME_FORMAT);
    }

    /** Reads cases, proceedings and courts once per answer; a page of hearings shares a few of each. */
    private final class Lookups {

        private final Map<UUID, CaseSummary> legalCases = new HashMap<>();
        private final Map<UUID, ProceedingSummary> proceedingSummaries = new HashMap<>();
        private final Map<UUID, Court> courtRecords = new HashMap<>();

        CaseSummary legalCase(UUID id) {
            return legalCases.computeIfAbsent(id, key -> cases.find(key).orElseThrow(() -> new NotFoundException("case")));
        }

        ProceedingSummary proceeding(UUID id) {
            return proceedingSummaries.computeIfAbsent(id, HearingService.this::proceeding);
        }

        UUID nextOf(UUID id) {
            return hearings.findByAdjournedFromId(id).map(Hearing::getId).orElse(null);
        }

        HearingRequests.Response respond(Hearing hearing, LocalDate today) {
            return respond(hearing, legalCase(hearing.getCaseId()), proceeding(hearing.getProceedingId()),
                    nextOf(hearing.getId()), today);
        }

        HearingRequests.Response respond(Hearing hearing, CaseSummary legalCase, ProceedingSummary proceeding,
                UUID nextHearingId, LocalDate today) {
            Court court = courtRecords.computeIfAbsent(proceeding.courtId(),
                    key -> courts.find(key).orElseThrow(() -> new NotFoundException("court")));
            return HearingRequests.Response.of(hearing, legalCase, proceeding, court, nextHearingId, today);
        }
    }
}
