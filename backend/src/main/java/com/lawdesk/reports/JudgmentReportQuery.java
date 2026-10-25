package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;

import com.lawdesk.proceedings.Outcome;
import com.lawdesk.proceedings.Stage;

/**
 * Parameters of the judgments report and its export (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032, LD-034). Bound as
 * one object, so a malformed value is a field error ({@code typeMismatch}); only whitelisted sort fields pass. The
 * finality filter is the {@code final} parameter, a word Java reserves.
 */
record JudgmentReportQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        List<Outcome> outcome,
        List<Stage> stage,
        UUID courtId,
        @BindParam("final") Boolean finalJudgment,
        @Min(0) Integer page,
        @Min(1) Integer size,
        @Pattern(regexp = SORT) String sort) {

    static final String SORT = "(judgmentDate|outcome|stage)(,(asc|desc))?";

    JudgmentReportQuery {
        outcome = outcome == null ? List.of() : List.copyOf(outcome);
        stage = stage == null ? List.of() : List.copyOf(stage);
    }
}
