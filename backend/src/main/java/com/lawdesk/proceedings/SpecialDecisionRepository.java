package com.lawdesk.proceedings;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpecialDecisionRepository extends JpaRepository<SpecialDecision, UUID> {

    List<SpecialDecision> findByProceedingIdIn(Collection<UUID> proceedingIds);
}
