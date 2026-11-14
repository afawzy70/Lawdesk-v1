package com.lawdesk.hearings;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.NextHearingRecorder;
import com.lawdesk.proceedings.ProceedingLookup;

/** Schedules the next hearing of a case registered through the government intake (LD-045), reusing {@link HearingService}. */
@Component
class NextHearingIntake implements NextHearingRecorder {

    private final HearingService hearings;
    private final ProceedingLookup proceedings;

    NextHearingIntake(HearingService hearings, ProceedingLookup proceedings) {
        this.hearings = hearings;
        this.proceedings = proceedings;
    }

    @Override
    @Transactional
    public void record(UUID caseId, LocalDate hearingDate, LocalTime hearingTime, String notes) {
        UUID proceedingId = proceedings.forCase(caseId).getFirst().id();
        try {
            hearings.create(proceedingId, new HearingRequests.Create(hearingDate, hearingTime, HearingPurpose.OTHER,
                    notes, null, null, null, null, null, null, null, null));
        } catch (FieldErrorException e) {
            throw new FieldErrorException("nextHearing." + e.field(), e.code());
        }
    }
}
