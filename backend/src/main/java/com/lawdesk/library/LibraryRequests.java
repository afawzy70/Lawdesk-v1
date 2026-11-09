package com.lawdesk.library;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the library API (LD-052). */
final class LibraryRequests {

    static final String COURT_LEVEL = "FIRST_INSTANCE|APPEAL|CASSATION|CONSTITUTIONAL|OTHER";
    static final String CASE_TYPE = "CIVIL|CRIMINAL|COMMERCIAL|LABOR|FAMILY|ADMINISTRATIVE|CONSTITUTIONAL|OTHER";
    static final String DIRECTION = "FOR_AUTHORITY|AGAINST_AUTHORITY|OTHER";
    static final String FINALITY = "FINAL|NOT_FINAL|NOT_SPECIFIED";
    static final String CONFIDENTIALITY = "NORMAL|RESTRICTED";
    static final String DIMENSION = "CASE_KIND|LEGAL_TOPIC|REQUEST_TYPE|DEFENSE_TYPE|OUTCOME|SECTOR";
    static final String RELATION = "APPEALED|UPHELD|MODIFIED|OVERTURNED|LATER|DEPARTED_FROM";
    private static final int LONG_TEXT = 200_000;

    private LibraryRequests() {
    }

    /**
     * A judgment to register or to replace (update sends the whole record and the version it read). Only the court, the
     * subject, the source and its verification are required; anything else may be left unspecified.
     */
    record JudgmentWrite(@NotBlank @Size(max = 200) String courtName,
            @Pattern(regexp = COURT_LEVEL) String courtLevel, @Size(max = 120) String chamber,
            @Size(max = 60) String caseNumber, @Min(1900) @Max(2100) Integer caseYear, LocalDate judgmentDate,
            @Pattern(regexp = CASE_TYPE) String caseType, @NotBlank @Size(max = 1000) String subject,
            @Size(max = 1000) String partiesDescription, @Size(max = LONG_TEXT) String summary,
            @Size(max = LONG_TEXT) String operativeText, @Size(max = LONG_TEXT) String reasons,
            @Size(max = LONG_TEXT) String legalTexts, @Size(max = LONG_TEXT) String requestsAddressed,
            @Size(max = LONG_TEXT) String requestResults, @Pattern(regexp = DIRECTION) String direction,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> keywords,
            @Pattern(regexp = FINALITY) String finality, @Pattern(regexp = CONFIDENTIALITY) String confidentiality,
            @NotBlank @Size(max = 1000) String sourceReference, @NotBlank @Size(max = 1000) String verificationNote,
            @Size(max = 500) String copyReference, @Size(max = 100) List<@NotNull UUID> classificationIds,
            Boolean allowDuplicate, Long version) {
    }

    record Withdraw(@NotBlank @Size(max = 500) String reason, @NotNull Long version) {
    }

    record Versioned(@NotNull Long version) {
    }

    record PrincipleWrite(@NotBlank @Size(max = LONG_TEXT) String principleText, @Size(max = 300) String legalTopic,
            @Size(max = LONG_TEXT) String legalBasis, @Size(max = LONG_TEXT) String factsApplied,
            @Size(max = LONG_TEXT) String scopeNote, @Size(max = LONG_TEXT) String memberNotes, Long version) {
    }

    record RelationWrite(@NotNull @Pattern(regexp = RELATION) String relationType, UUID relatedJudgmentId,
            @Size(max = 500) String relatedCitation, LocalDate relationDate,
            @NotBlank @Size(max = 1000) String sourceReference, @Size(max = 2000) String notes) {
    }

    record ClassificationCreate(@NotNull @Pattern(regexp = DIMENSION) String dimension,
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,40}") String code, @NotBlank @Size(max = 200) String labelAr,
            @Size(max = 200) String labelEn, Integer sortOrder) {
    }

    record ClassificationUpdate(@NotBlank @Size(max = 200) String labelAr, @Size(max = 200) String labelEn,
            @NotNull Boolean active, Integer sortOrder, @NotNull Long version) {
    }

    record PrecedentLink(@NotNull UUID judgmentId, @NotBlank @Size(max = 2000) String reason,
            @Size(max = 2000) String similarityNote, @Pattern(regexp = "MANUAL|SUGGESTION") String origin) {
    }
}
