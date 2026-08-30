package fr.projetcompensation.gymbuddy.posts;

import java.time.Instant;
import java.util.UUID;

public record Post(
        UUID id,
        UUID authorId,
        String body,
        PostVisibility visibility,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt,
        Instant hiddenAt,
        String hiddenReason) {

    boolean deleted() {
        return deletedAt != null;
    }

    boolean hidden() {
        return hiddenAt != null;
    }

    Post withBody(String body, Instant editedAt) {
        return new Post(id, authorId, body, visibility, createdAt, editedAt, deletedAt, hiddenAt, hiddenReason);
    }

    Post deleted(Instant at) {
        return new Post(id, authorId, body, visibility, createdAt, editedAt, at, hiddenAt, hiddenReason);
    }
}
