package fr.projetcompensation.gymbuddy.events;

import java.time.Instant;
import java.util.UUID;

public record EventOccurrence(UUID id, UUID eventId, Instant startsAt, Instant cancelledAt) {

    boolean cancelled() {
        return cancelledAt != null;
    }

    boolean past(Instant now) {
        return !startsAt.isAfter(now);
    }

    EventOccurrence cancelled(Instant at) {
        return new EventOccurrence(id, eventId, startsAt, at);
    }
}
