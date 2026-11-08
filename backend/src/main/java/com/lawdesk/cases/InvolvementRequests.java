package com.lawdesk.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies and views of {@code /api/v1/cases/{id}/involvements} (requirements section 6). */
final class InvolvementRequests {

    private InvolvementRequests() {
    }

    /** At least a unit of the structure or a name must identify the involved unit. */
    record Create(UUID orgUnitId, @Size(max = 200) String sectorName, @Size(max = 200) String departmentName,
            @Size(max = 200) String sectionName, Boolean primary, @Size(max = 200) String contactName,
            @Size(max = 300) String contactInfo, LocalDate referralDate, @Size(max = 1000) String requestedFromUnit,
            @Size(max = 2000) String notes) {

        Create {
            primary = primary != null && primary;
            sectorName = Text.trimToNull(sectorName);
            departmentName = Text.trimToNull(departmentName);
            sectionName = Text.trimToNull(sectionName);
            contactName = Text.trimToNull(contactName);
            contactInfo = Text.trimToNull(contactInfo);
            requestedFromUnit = Text.trimToNull(requestedFromUnit);
            notes = Text.trimToNull(notes);
        }
    }

    record Update(Boolean primary, @Size(max = 200) String contactName, @Size(max = 300) String contactInfo,
            LocalDate referralDate, @Size(max = 1000) String requestedFromUnit, @Size(max = 2000) String notes,
            @NotNull Long version) {

        Update {
            primary = primary != null && primary;
            contactName = Text.trimToNull(contactName);
            contactInfo = Text.trimToNull(contactInfo);
            requestedFromUnit = Text.trimToNull(requestedFromUnit);
            notes = Text.trimToNull(notes);
        }
    }

    record View(UUID id, UUID caseId, UUID orgUnitId, boolean primary, String sectorName, String departmentName,
            String sectionName, String contactName, String contactInfo, LocalDate referralDate,
            String requestedFromUnit, String notes, long version, Instant createdAt, Instant removedAt) {
    }
}
