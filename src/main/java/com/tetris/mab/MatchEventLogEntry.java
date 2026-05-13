package com.tetris.mab;

import java.util.Collections;
import java.util.Map;

/**
 * Single immutable entry in the match event log.
 *
 * @param sequenceNumber  monotonic, assigned by the match coordinator
 * @param eventType       short tag, e.g. "PIECE_LOCKED", "LINES_CLEARED"
 * @param participantId   participant the event is about (nullable for global events)
 * @param message         human-readable description
 * @param metadata        optional map of extra fields (immutable copy)
 */
public record MatchEventLogEntry(long sequenceNumber,
                                 String eventType,
                                 ParticipantId participantId,
                                 String message,
                                 Map<String, Object> metadata) {

    public MatchEventLogEntry {
        eventType = eventType == null ? "" : eventType;
        message = message == null ? "" : message;
        if (metadata == null || metadata.isEmpty()) {
            metadata = Collections.emptyMap();
        } else {
            // Tolerate null values (Map.copyOf rejects them); preserve
            // insertion order for readable logs.
            metadata = Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(metadata));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(sequenceNumber).append(' ').append(eventType);
        if (participantId != null) sb.append(' ').append(participantId);
        if (!message.isEmpty()) sb.append(" — ").append(message);
        if (!metadata.isEmpty()) sb.append(' ').append(metadata);
        return sb.toString();
    }
}
