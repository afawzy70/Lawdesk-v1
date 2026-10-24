package com.lawdesk.proceedings;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProceedingRepository extends JpaRepository<Proceeding, UUID> {

    List<Proceeding> findByCaseId(UUID caseId);

    /** Whether another proceeding (not {@code self}) has this number and year at this court and stage. */
    @Query("""
            select count(p) > 0 from Proceeding p
            where p.courtId = :courtId and p.courtCaseYear = :year and lower(p.courtCaseNumber) = lower(:number)
              and p.stage = :stage and p.id <> :self""")
    boolean numberTaken(UUID courtId, int year, String number, Stage stage, UUID self);
}
