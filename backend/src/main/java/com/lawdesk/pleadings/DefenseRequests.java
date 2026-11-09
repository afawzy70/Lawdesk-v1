package com.lawdesk.pleadings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies and views of the defences API (requirements section 17). */
final class DefenseRequests {

    private DefenseRequests() {
    }

    record Create(@NotBlank @Size(max = 300) String title, @NotNull DefenseKind kind, @Size(max = 100000) String argument,
            @Size(max = 100000) String legalBasis, @Size(max = 100000) String facts, @Size(max = 100000) String legalTexts,
            UUID memoDocumentId, LocalDate raisedOn, UUID raisedStageId, UUID preparedBy, @Size(max = 2000) String notes,
            UUID templateId) {

        Create {
            title = title == null ? null : title.strip();
            argument = CaseRows.blankToNull(argument);
            legalBasis = CaseRows.blankToNull(legalBasis);
            facts = CaseRows.blankToNull(facts);
            legalTexts = CaseRows.blankToNull(legalTexts);
            notes = CaseRows.blankToNull(notes);
        }
    }

    record Update(@NotBlank @Size(max = 300) String title, @NotNull DefenseKind kind, @Size(max = 100000) String argument,
            @Size(max = 100000) String legalBasis, @Size(max = 100000) String facts, @Size(max = 100000) String legalTexts,
            UUID memoDocumentId, LocalDate raisedOn, UUID raisedStageId, UUID preparedBy, @Size(max = 2000) String notes,
            UUID templateId, @NotNull Long version) {

        Update {
            title = title == null ? null : title.strip();
            argument = CaseRows.blankToNull(argument);
            legalBasis = CaseRows.blankToNull(legalBasis);
            facts = CaseRows.blankToNull(facts);
            legalTexts = CaseRows.blankToNull(legalTexts);
            notes = CaseRows.blankToNull(notes);
        }

        Create asCreate() {
            return new Create(title, kind, argument, legalBasis, facts, legalTexts, memoDocumentId, raisedOn, raisedStageId,
                    preparedBy, notes, templateId);
        }
    }

    record View(UUID id, UUID caseId, int seq, String title, DefenseKind kind, String argument, String legalBasis,
            String facts, String legalTexts, UUID memoDocumentId, String memoDocumentTitle, LocalDate raisedOn,
            UUID raisedStageId, UUID preparedBy, String preparedByName, String notes, UUID templateId, int evidenceCount,
            long version, Instant createdAt, Instant updatedAt) {
    }

    record AssertionBody(@NotNull AssertionStatus status, LocalDate onDate, UUID memoDocumentId) {
    }

    record AssertionView(UUID defenseId, int defenseSeq, UUID proceedingId, String stage, AssertionStatus status,
            LocalDate onDate, UUID memoDocumentId, String memoDocumentTitle) {
    }

    record LinkedEvidence(UUID evidenceId, String name, EvidenceRole role, EvidenceImportance importance, UUID documentId,
            boolean submittedToCourt) {
    }

    /** The defence with what happened to it: its assertion in each stage, the court's position in each judgment, its evidence. */
    record Detail(View defense, List<AssertionView> assertions, List<ResultRequests.CaseDefensePosition> positions,
            List<LinkedEvidence> evidence) {
    }

    record TemplateBody(@NotBlank @Size(max = 300) String title, @NotNull DefenseKind kind, @Size(max = 100000) String text,
            @Size(max = 100000) String legalBasis, TemplateStatus status, Long version) {

        TemplateBody {
            title = title == null ? null : title.strip();
            text = CaseRows.blankToNull(text);
            legalBasis = CaseRows.blankToNull(legalBasis);
        }
    }

    record TemplateView(UUID id, String title, DefenseKind kind, String text, String legalBasis, TemplateStatus status,
            UUID reviewedBy, Instant reviewedAt, long version, Instant updatedAt) {
    }
}
