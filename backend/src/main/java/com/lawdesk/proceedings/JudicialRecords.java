package com.lawdesk.proceedings;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.courts.Court;
import com.lawdesk.courts.CourtService;

/** Lookups shared by the proceeding and judgment services: cases (through B's port), courts and proceedings. */
@Component
class JudicialRecords {

    /** Stage order, then filing date, then the order of recording. */
    static final Comparator<Proceeding> LADDER_ORDER = Comparator.comparing(Proceeding::getStage)
            .thenComparing(Proceeding::getFilingDate).thenComparing(Proceeding::getCreatedAt);

    private final CaseLookup cases;
    private final CourtService courts;
    private final ProceedingRepository proceedings;

    JudicialRecords(CaseLookup cases, CourtService courts, ProceedingRepository proceedings) {
        this.cases = cases;
        this.courts = courts;
        this.proceedings = proceedings;
    }

    void requireCase(UUID caseId) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
    }

    Court court(UUID id) {
        return courts.find(id).orElseThrow(() -> new NotFoundException("court"));
    }

    Proceeding proceeding(UUID id) {
        return proceedings.findById(id).orElseThrow(() -> new NotFoundException("proceeding"));
    }

    /** The case's proceedings in ladder order. */
    List<Proceeding> proceedingsOf(UUID caseId) {
        return proceedings.findByCaseId(caseId).stream().sorted(LADDER_ORDER).toList();
    }

    void recordEvent(UUID caseId, String type, String summary) {
        cases.recordEvent(caseId, type, summary);
    }

    /** A case holds a handful of proceedings, so lists are ordered and paged in memory. */
    static <T> Page<T> page(List<T> all, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /** PostgreSQL keeps microseconds; answers show what was stored. */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
