package fr.projetcompensation.gymbuddy.comments;

import java.time.Instant;
import java.util.UUID;

public record Comment(
        UUID id,
        UUID postId,
        UUID authorId,
        UUID parentId,
        String body,
        int depth,
        Instant createdAt,
        Instant deletedAt,
        Instant hiddenAt) {

    public static final String TOMBSTONE = "comment deleted";
    public static final int MAX_DEPTH = 4;
    public static final int MAX_BODY = 1000;

    boolean deleted() {
        return deletedAt != null;
    }

    boolean hidden() {
        return hiddenAt != null;
    }

    boolean tombstoned() {
        return deleted() || hidden();
    }

    String visibleBody() {
        return tombstoned() ? TOMBSTONE : body;
    }

    Comment tombstone(Instant at) {
        return new Comment(id, postId, authorId, parentId, TOMBSTONE, depth, createdAt, at, hiddenAt);
    }
}
