package fr.projetcompensation.gymbuddy.search;

import java.util.Optional;
import java.util.UUID;

record RankIdCursor(double rank, UUID id) {

    static Optional<RankIdCursor> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        int split = raw.lastIndexOf(':');
        if (split <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RankIdCursor(
                    Double.parseDouble(raw.substring(0, split)), UUID.fromString(raw.substring(split + 1))));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    String encode() {
        // %.12f rounds some scores so parse() yields a different double and after() re-includes the last hit.
        return Double.toString(rank) + ":" + id;
    }

    boolean after(double otherRank, UUID otherId) {
        int cmp = Double.compare(rank, otherRank);
        if (cmp != 0) {
            return cmp > 0;
        }
        return id.compareTo(otherId) > 0;
    }
}
