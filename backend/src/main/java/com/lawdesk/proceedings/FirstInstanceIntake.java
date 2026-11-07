package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.FirstInstanceRecorder;

/** Records the first-instance proceeding of a case registered through the government intake (LD-045). */
@Component
class FirstInstanceIntake implements FirstInstanceRecorder {

    private final ProceedingService proceedings;

    FirstInstanceIntake(ProceedingService proceedings) {
        this.proceedings = proceedings;
    }

    @Override
    @Transactional
    public void record(UUID caseId, UUID courtId, String courtCaseNumber, int courtCaseYear, LocalDate filingDate) {
        proceedings.create(caseId, new ProceedingRequests.Create(Stage.FIRST_INSTANCE, courtId, courtCaseNumber,
                courtCaseYear, filingDate, null));
    }
}
