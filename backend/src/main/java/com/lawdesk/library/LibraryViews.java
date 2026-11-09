package com.lawdesk.library;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes of the library API (LD-052). */
final class LibraryViews {

    private LibraryViews() {
    }

    record Classification(UUID id, String dimension, String code, String labelAr, String labelEn, String origin,
            boolean active, int sortOrder, long version, Instant updatedAt) {
    }

    /** Where a judgment sits: enough to cite it. {@code hasCopy}: a saved electronic copy is recorded. */
    record JudgmentRef(UUID id, String courtName, String courtLevel, String caseNumber, Integer caseYear,
            LocalDate judgmentDate, String caseType, String subject, String finality, String status,
            String sourceReference, boolean hasCopy) {
    }

    record ListItem(UUID id, String courtName, String courtLevel, String chamber, String caseNumber, Integer caseYear,
            LocalDate judgmentDate, String caseType, String subject, String direction, String finality,
            String confidentiality, String status, List<Classification> classifications, int principleCount,
            List<String> relationTypes, double score, List<String> matchedIn) {
    }

    record Principle(UUID id, UUID judgmentId, String principleText, String legalTopic, String legalBasis,
            String factsApplied, String scopeNote, String memberNotes, String reviewStatus, UUID reviewedBy,
            Instant reviewedAt, long version, Instant createdAt, UUID createdBy, Instant updatedAt, String courtName,
            String caseNumber, Integer caseYear, LocalDate judgmentDate) {
    }

    /** One recorded fact about a judgment; {@code direction} OUTGOING: it happened to this judgment, INCOMING: this judgment is the other side. */
    record Relation(UUID id, String direction, String relationType, UUID judgmentId, UUID otherJudgmentId,
            String otherSubject, String otherCourtName, String otherCaseNumber, Integer otherCaseYear,
            LocalDate otherJudgmentDate, String relatedCitation, LocalDate relationDate, String sourceReference,
            String notes, Instant createdAt, UUID createdBy) {
    }

    record Relations(List<Relation> outgoing, List<Relation> incoming) {
    }

    /** What the library has recorded about the standing of a judgment; it never decides it. */
    record Validity(UUID judgmentId, String finality, String status, int relationCount, List<String> relationTypes,
            List<String> warnings, boolean legalValidityDecided, String note) {
    }

    record LinkedCase(UUID linkId, UUID caseId, String referenceNo, String title, String reason, Instant linkedAt) {
    }

    record Judgment(UUID id, String courtName, String courtLevel, String chamber, String caseNumber,
            Integer caseYear, LocalDate judgmentDate, String caseType, String subject, String partiesDescription,
            boolean partiesMasked, String summary, String operativeText, String reasons, String legalTexts,
            String requestsAddressed, String requestResults, String direction, List<String> keywords, String finality,
            String confidentiality, String sourceReference, String verificationNote, String copyReference,
            String status, String withdrawnReason, Instant withdrawnAt, List<Classification> classifications,
            List<Principle> principles, Relations relations, Validity validity, List<LinkedCase> linkedCases,
            long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
    }

    record Precedent(UUID id, UUID caseId, JudgmentRef judgment, String reason, String similarityNote, String origin,
            List<String> relationTypes, Instant createdAt, UUID createdBy) {
    }

    record Reason(String code, int points, List<String> terms) {
    }

    record Difference(String code, String detail) {
    }

    record Suggestion(JudgmentRef judgment, int score, List<Reason> reasons, List<Difference> differences,
            List<String> relationTypes, List<String> warnings, boolean alreadyLinked) {
    }

    record Basis(List<String> terms, String caseType, List<String> courtLevels, boolean defensesIncluded) {
    }

    record Suggestions(UUID caseId, Basis basis, List<Suggestion> items, String note) {
    }

    record Count(String key, String label, long count) {
    }

    record ClassificationCount(String dimension, UUID classificationId, String code, String labelAr, String labelEn,
            long count) {
    }

    record Issue(String topic, long principleCount, long judgmentCount, LocalDate firstJudgmentDate,
            LocalDate lastJudgmentDate) {
    }

    record Summary(long total, long active, long withdrawn, List<Count> byCourt, List<Count> byYear,
            List<Count> byDirection, List<Count> byFinality, List<ClassificationCount> byClassification,
            List<Issue> frequentIssues, String note) {
    }
}
