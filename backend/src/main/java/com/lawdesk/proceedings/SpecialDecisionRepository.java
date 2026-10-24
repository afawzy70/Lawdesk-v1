package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpecialDecisionRepository extends JpaRepository<SpecialDecision, UUID> {

    List<SpecialDecision> findByProceedingIdIn(Collection<UUID> proceedingIds);

    /** The earliest decision date in the proceeding, if any; read it with the proceeding locked (QAD-024). */
    @Query("select min(d.decisionDate) from SpecialDecision d where d.proceedingId = :proceedingId")
    Optional<LocalDate> earliestDecisionDate(UUID proceedingId);
}
