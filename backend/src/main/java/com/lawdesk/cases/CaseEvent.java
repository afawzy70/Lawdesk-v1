package com.lawdesk.cases;

import java.time.Instant;
import java.util.UUID;

/** One timeline entry (LD-014). */
record CaseEvent(UUID id, String type, String summary, Instant occurredAt, UUID actorId) {
}
