package fr.projetcompensation.gymbuddy.friends;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record InstantIdCursor(Instant at, UUID id) {

    public static Optional<InstantIdCursor> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        int split = raw.lastIndexOf(':');
        if (split <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new InstantIdCursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, split))),
                    UUID.fromString(raw.substring(split + 1))));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public String encode() {
        return at.toEpochMilli() + ":" + id;
    }
}
