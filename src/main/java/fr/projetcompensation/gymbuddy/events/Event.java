package fr.projetcompensation.gymbuddy.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Event(
        UUID id,
        UUID organizerId,
        String title,
        String description,
        String activity,
        String place,
        Double lat,
        Double lng,
        Instant startsAt,
        int durationMin,
        EventVisibility visibility,
        int capacity,
        String recurrence,
        List<String> tags,
        UUID coverMediaId,
        Instant cancelledAt,
        boolean updatedAfterAccept,
        Instant createdAt,
        Instant hiddenAt) {

    public Event {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    boolean instant() {
        return recurrence == null || recurrence.isBlank();
    }

    String kindWire() {
        return instant() ? "instant" : "recurring";
    }

    boolean cancelled() {
        return cancelledAt != null;
    }

    boolean hidden() {
        return hiddenAt != null;
    }

    Event cancelled(Instant at) {
        return new Event(
                id,
                organizerId,
                title,
                description,
                activity,
                place,
                lat,
                lng,
                startsAt,
                durationMin,
                visibility,
                capacity,
                recurrence,
                tags,
                coverMediaId,
                at,
                updatedAfterAccept,
                createdAt,
                hiddenAt);
    }

    Event withDetails(
            String title,
            String description,
            String activity,
            String place,
            Double lat,
            Double lng,
            Instant startsAt,
            int durationMin,
            List<String> tags,
            UUID coverMediaId,
            boolean updatedAfterAccept) {
        return new Event(
                id,
                organizerId,
                title,
                description,
                activity,
                place,
                lat,
                lng,
                startsAt,
                durationMin,
                visibility,
                capacity,
                recurrence,
                tags,
                coverMediaId,
                cancelledAt,
                updatedAfterAccept,
                createdAt,
                hiddenAt);
    }
}
