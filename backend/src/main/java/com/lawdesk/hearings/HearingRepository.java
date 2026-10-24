package com.lawdesk.hearings;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface HearingRepository extends JpaRepository<Hearing, UUID> {

    /** Reads the hearing with {@code select ... for update}, so racing writes to it are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hearing h where h.id = :id")
    Optional<Hearing> lockById(UUID id);

    /** Oldest first: date, then time with untimed hearings first, then the order of recording. */
    @Query(value = """
            select h from Hearing h where h.caseId = :caseId
            order by h.hearingDate, h.hearingTime nulls first, h.createdAt, h.id""",
            countQuery = "select count(h) from Hearing h where h.caseId = :caseId")
    Page<Hearing> findForCase(UUID caseId, Pageable pageable);

    /** The calendar: hearings of every case dated {@code from}…{@code to}, optionally of one status. */
    @Query(value = """
            select h from Hearing h
            where h.hearingDate between :from and :to and (:status is null or h.status = :status)
            order by h.hearingDate, h.hearingTime nulls first, h.createdAt, h.id""",
            countQuery = """
                    select count(h) from Hearing h
                    where h.hearingDate between :from and :to and (:status is null or h.status = :status)""")
    Page<Hearing> findBetween(LocalDate from, LocalDate to, HearingStatus status, Pageable pageable);

    Optional<Hearing> findByAdjournedFromId(UUID adjournedFromId);

    List<Hearing> findByAdjournedFromIdIn(Collection<UUID> adjournedFromIds);
}
