package fr.projetcompensation.gymbuddy.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Message(
        UUID id,
        UUID conversationId,
        UUID senderId,
        MessageType type,
        String body,
        UUID mediaId,
        Instant createdAt,
        Instant deletedAt) {

    public static final String TOMBSTONE = "message deleted";
    public static final int MAX_BODY = 4000;
    public static final Duration DELETE_WINDOW = Duration.ofMinutes(10);

    public boolean deleted() {
        return deletedAt != null;
    }

    public String visibleBody() {
        return deleted() ? TOMBSTONE : body;
    }

    public UUID visibleMediaId() {
        return deleted() ? null : mediaId;
    }

    public boolean canTombstone(UUID callerId, Instant now) {
        return senderId.equals(callerId) && !deleted() && !now.isAfter(createdAt.plus(DELETE_WINDOW));
    }

    public Message tombstone(Instant at) {
        return new Message(id, conversationId, senderId, type, TOMBSTONE, mediaId, createdAt, at);
    }
}
