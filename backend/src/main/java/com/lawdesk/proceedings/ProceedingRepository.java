package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProceedingRepository extends JpaRepository<Proceeding, UUID> {

    List<Proceeding> findByCaseId(UUID caseId);

    /**
     * Reads the proceeding with {@code select ... for update}. Every write that compares dates across a proceeding,
     * its judgment and the proceedings attacking it takes this lock first, so the comparison sees committed data
     * and cannot interleave with another such write (QAD-024).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proceeding p where p.id = :id")
    Optional<Proceeding> lockById(UUID id);

    /** The earliest filing date of the appeals or cassations that attack {@code parentId}, if any. */
    @Query("select min(p.filingDate) from Proceeding p where p.parentProceedingId = :parentId")
    Optional<LocalDate> earliestChallengeFiling(UUID parentId);

    /** Whether another proceeding (not {@code self}) has this number and year at this court and stage. */
    @Query("""
            select count(p) > 0 from Proceeding p
            where p.courtId = :courtId and p.courtCaseYear = :year and lower(p.courtCaseNumber) = lower(:number)
              and p.stage = :stage and p.id <> :self""")
    boolean numberTaken(UUID courtId, int year, String number, Stage stage, UUID self);
}
