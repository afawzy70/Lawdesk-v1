package com.lawdesk.courts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Court reference data (LD-018). Other modules read courts through {@link #find(UUID)}. */
@Service
public class CourtService {

    /** Fields a list may be sorted by; others are ignored (docs/delivery/PHASE-4-ACCEPTANCE.md, common rule 4). */
    private static final Set<String> SORTABLE =
            Set.of("code", "nameAr", "nameEn", "level", "governorate", "active", "createdAt", "updatedAt");
    private static final Sort DEFAULT_ORDER = Sort.by("code");

    private final CourtRepository courts;
    private final CurrentActor currentActor;

    CourtService(CourtRepository courts, CurrentActor currentActor) {
        this.courts = courts;
        this.currentActor = currentActor;
    }

    /** A court by id, for other modules (proceedings check the level and the active flag). */
    @Transactional(readOnly = true)
    public Optional<Court> find(UUID id) {
        return courts.findById(id);
    }

    @Transactional(readOnly = true)
    Court get(UUID id) {
        return courts.findById(id).orElseThrow(() -> new NotFoundException("court"));
    }

    @Transactional(readOnly = true)
    Page<Court> list(CourtLevel level, Boolean active, Pageable pageable) {
        Sort sort = Sort.by(pageable.getSort().filter(order -> SORTABLE.contains(order.getProperty())).toList());
        Sort order = sort.isSorted() ? sort.and(DEFAULT_ORDER) : DEFAULT_ORDER;
        return courts.search(level, active, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order));
    }

    @Transactional
    Court create(CourtRequests.Create request) {
        ActorRef actor = currentActor.require();
        Court court = new Court(actor, now());
        describe(court, request);
        return store(court);
    }

    @Transactional
    Court update(UUID id, CourtRequests.Update request) {
        ActorRef actor = currentActor.require();
        Court court = get(id);
        if (court.getVersion() != request.version()) {
            throw new ConflictException("court was changed by someone else");
        }
        describe(court, request.description());
        court.setActive(request.active());
        court.touch(actor, now());
        return store(court);
    }

    private void describe(Court court, CourtRequests.Create request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        courts.findByCode(code).filter(other -> !other.getId().equals(court.getId())).ifPresent(other -> {
            throw new ConflictException("court code already used");
        });
        court.describe(code, request.nameAr().trim(), request.nameEn().trim(), request.level(),
                blankToNull(request.governorate()));
    }

    /** Flushes now, so a concurrent insert of the same code becomes a 409 instead of a failure at commit. */
    private Court store(Court court) {
        try {
            return courts.saveAndFlush(court);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("court code already used");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** PostgreSQL keeps microseconds; the answer shows what was stored. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
