package com.lawdesk.courts;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CourtRepository extends JpaRepository<Court, UUID> {

    Optional<Court> findByCode(String code);

    @Query("""
            select c from Court c
            where (:level is null or c.level = :level) and (:active is null or c.active = :active)""")
    Page<Court> search(CourtLevel level, Boolean active, Pageable pageable);
}
