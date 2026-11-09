package com.lawdesk.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.lawdesk.assistant.AssistantProvider.Block;
import com.lawdesk.assistant.AssistantProvider.Source;

import tools.jackson.databind.JsonNode;

/** Request and answer shapes of the legal assistant API (LD-059, requirements section 22). */
final class AssistantRequests {

    private AssistantRequests() {
    }

    /**
     * {@code task} says what is asked; {@code caseId}, {@code question}, {@code documentId} and {@code otherDocumentId} are the
     * inputs that task needs. {@code saveDraft} keeps the result as a non-final draft of the user's (a memorandum draft is always
     * kept). {@code sessionId} continues an earlier conversation of the same user.
     */
    record Ask(@NotNull AssistantTask task, UUID caseId, @Size(max = 1000) String question, UUID documentId, UUID otherDocumentId,
            UUID sessionId, Boolean saveDraft) {

        Ask {
            question = question == null || question.isBlank() ? null : question.strip();
        }

        boolean keep() {
            return saveDraft != null && saveDraft;
        }
    }

    /**
     * The answer. {@code outcome} is ANSWERED or INSUFFICIENT_SOURCES; when insufficient, {@code refusal} says why and no
     * block or source is returned - the assistant does not answer without sources. {@code operational} is false while no
     * provider is approved, and {@code mode} says the answer comes from a non-production test provider.
     */
    record Answer(Long queryId, UUID sessionId, AssistantTask task, String outcome, String refusal, boolean operational,
            String reason, String mode, AssistantProvider.Info provider, UUID caseId, List<Block> blocks, List<Source> sources,
            UUID outputId, boolean isFinal) {
    }

    record Review(@NotNull @Pattern(regexp = "APPROVED|DISCARDED") String decision, @Size(max = 500) String note) {
    }

    /** A draft the assistant produced. It is never final: {@code isFinal} is always false. */
    record Output(UUID id, UUID sessionId, UUID caseId, String kind, String title, String status, boolean isFinal,
            boolean nonProduction, JsonNode content, UUID reviewedBy, Instant reviewedAt, String reviewNote, Instant createdAt) {
    }
}
