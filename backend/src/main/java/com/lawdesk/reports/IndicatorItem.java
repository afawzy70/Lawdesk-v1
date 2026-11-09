package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of an indicator's list. {@code entity} says what it is; {@code status} and {@code detail} are enumeration names
 * (case status, hearing status and purpose, judgment outcome and stage, task or request status) that screens and
 * exports translate; {@code title} is free text where the row has one (a task, a request, a deadline's action).
 */
public record IndicatorItem(IndicatorCode.Entity entity, UUID id, UUID caseId, String caseReferenceNo, String caseTitle,
        String title, LocalDate date, String status, String detail, String statusLabel, String detailLabel) {

    /** The row without labels (the definitions build rows this way; the language is applied afterwards). */
    public IndicatorItem(IndicatorCode.Entity entity, UUID id, UUID caseId, String caseReferenceNo, String caseTitle,
            String title, LocalDate date, String status, String detail) {
        this(entity, id, caseId, caseReferenceNo, caseTitle, title, date, status, detail, null, null);
    }

    /** The same row with its status and detail written in the language of {@code labels}. */
    IndicatorItem labelled(WpLabels labels) {
        return new IndicatorItem(entity, id, caseId, caseReferenceNo, caseTitle, title, date, status, detail,
                labels.value(entity, false, status), labels.value(entity, true, detail));
    }
}
