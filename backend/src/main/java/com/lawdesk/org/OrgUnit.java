package com.lawdesk.org;

import java.time.LocalDate;
import java.util.UUID;

/** A unit of the organisation tree (LD-050): the legal department's own units, or the authority's sectors and departments. */
public record OrgUnit(UUID id, String tree, UUID parentId, String level, String nameAr, String nameEn, boolean active,
        LocalDate validFrom, LocalDate validTo, long version) {
}
