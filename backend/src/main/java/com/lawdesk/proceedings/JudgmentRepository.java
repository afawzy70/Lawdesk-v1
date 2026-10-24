package com.lawdesk.proceedings;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JudgmentRepository extends JpaRepository<Judgment, UUID> {

    Optional<Judgment> findByProceedingId(UUID proceedingId);

    boolean existsByProceedingId(UUID proceedingId);

    List<Judgment> findByProceedingIdIn(Collection<UUID> proceedingIds);
}
