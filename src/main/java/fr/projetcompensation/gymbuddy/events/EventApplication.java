package fr.projetcompensation.gymbuddy.events;

import java.time.Instant;
import java.util.UUID;

public record EventApplication(
        UUID id,
        UUID eventId,
        UUID occurrenceId,
        UUID applicantId,
        EventApplicationStatus status,
        Instant createdAt,
        Instant respondedAt) {

    EventApplication withStatus(EventApplicationStatus status, Instant respondedAt) {
        return new EventApplication(id, eventId, occurrenceId, applicantId, status, createdAt, respondedAt);
    }
}
