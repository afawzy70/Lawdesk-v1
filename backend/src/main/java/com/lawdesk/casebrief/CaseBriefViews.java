package com.lawdesk.casebrief;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shapes of the case brief (LD-DOC rows of requirements section 22 that are plain queries over the system's own records).
 * Nothing here is model-generated: every {@link Entry} carries the record it was read from, and a fact the records do not hold
 * is reported as {@link #NOT_RECORDED}, never filled in.
 */
final class CaseBriefViews {

    /** The value of a status or field the records do not hold. */
    static final String NOT_RECORDED = "NOT_RECORDED";

    /** The section is not shown because the user lacks the permission to read its records. */
    static final String NOT_PERMITTED = "NOT_PERMITTED";

    static final String RECORDED = "RECORDED";

    private CaseBriefViews() {
    }

    /**
     * Where an entry comes from: the kind and id of the record, its date, and the document it cites. {@code page} is null
     * because none of these records stores a page number; the brief never invents one.
     */
    record Source(String type, UUID id, LocalDate date, UUID documentId, String documentTitle, Integer page) {
    }

    /** One line of the brief. {@code fields} are further recorded values; {@code children} are rulings of judgments. */
    record Entry(String kind, String title, String status, LocalDate date, String text, Source source,
            Map<String, String> fields, List<Entry> children) {
    }

    /**
     * A part of the brief: RECORDED (items found), NOT_RECORDED (the records hold none) or NOT_PERMITTED (the user may not read
     * them). {@code omitted} names kinds of records left out for lack of permission inside an otherwise shown section.
     */
    record Section(String status, List<Entry> items, List<String> omitted) {
    }

    record Brief(UUID caseId, String origin, boolean modelGenerated, String notice, LocalDate asOf, Entry caseData,
            Section timeline, Section lastAction, Section nextHearing, Section nextRequiredActions, Section afterLastHearing,
            Section requests, Section rejectedRequests, Section defenses, Section defensesNotRuledOn, Section openDeadlines,
            Section appealDeadlines, Section missingFiles, Section facts, Section evidence, Section ourPosition,
            Section legalTexts, Section linkedJudgments, Section similarJudgments) {
    }
}
