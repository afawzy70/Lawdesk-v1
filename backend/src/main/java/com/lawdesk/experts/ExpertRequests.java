package com.lawdesk.experts;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawdesk.proceedings.Stage;

/** Request and response bodies of the expert registry and referral API (LD-025). Text is trimmed first. */
final class ExpertRequests {

    static final String TIME = "HH:mm";
    static final String PHONE = "^[0-9 +()-]+$";

    private ExpertRequests() {
    }

    record ExpertInput(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String specialty,
            @Size(max = 200) String office,
            @Size(max = 32) @Pattern(regexp = PHONE) String phone) {

        ExpertInput {
            name = Rules.trim(name);
            specialty = Rules.trim(specialty);
            office = Rules.trimToNull(office);
            phone = Rules.trimToNull(phone);
        }
    }

    record ExpertUpdate(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String specialty,
            @Size(max = 200) String office,
            @Size(max = 32) @Pattern(regexp = PHONE) String phone,
            @NotNull Boolean active,
            @NotNull Long version) {

        ExpertUpdate {
            name = Rules.trim(name);
            specialty = Rules.trim(specialty);
            office = Rules.trimToNull(office);
            phone = Rules.trimToNull(phone);
        }
    }

    record Expert(UUID id, String name, String specialty, String office, String phone, boolean active, long version,
            Instant createdAt, Instant updatedAt) {
    }

    record ReferralCreate(
            @NotNull UUID expertId,
            @NotNull LocalDate referralDate,
            @Size(max = 4000) String mission,
            LocalDate reportDueDate) {

        ReferralCreate {
            mission = Rules.trimToNull(mission);
        }
    }

    record ReferralUpdate(@Size(max = 4000) String mission, LocalDate reportDueDate, @NotNull Long version) {

        ReferralUpdate {
            mission = Rules.trimToNull(mission);
        }
    }

    record ReportSubmission(@NotNull LocalDate reportSubmittedDate, @NotNull Long version) {
    }

    record Cancellation(@NotNull Long version) {
    }

    record SessionCreate(
            @NotNull LocalDate sessionDate,
            @JsonFormat(pattern = TIME) LocalTime sessionTime,
            @Size(max = 200) String location,
            @Size(max = 2000) String notes) {

        SessionCreate {
            location = Rules.trimToNull(location);
            notes = Rules.trimToNull(notes);
        }
    }

    record Session(UUID id, LocalDate sessionDate, @JsonFormat(pattern = TIME) LocalTime sessionTime, String location,
            String notes, Instant createdAt) {
    }

    record Referral(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, UUID proceedingId, Stage stage,
            String courtCaseNumber, int courtCaseYear, UUID expertId, String expertName, String expertSpecialty,
            LocalDate referralDate, String mission, LocalDate reportDueDate, ReferralStatus status,
            LocalDate reportSubmittedDate, boolean reportOverdue, List<Session> sessions, long version,
            Instant createdAt, Instant updatedAt) {
    }
}
