package fr.projetcompensation.gymbuddy.friends;

import java.time.Instant;
import java.util.UUID;

public record Friendship(
        UUID id, UUID requesterId, UUID addresseeId, FriendshipStatus status, Instant createdAt, Instant respondedAt) {

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    public UUID other(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    public boolean incomingFor(UUID userId) {
        return addresseeId.equals(userId);
    }

    public Friendship withStatus(FriendshipStatus status, Instant respondedAt) {
        return new Friendship(id, requesterId, addresseeId, status, createdAt, respondedAt);
    }

    public Friendship asBlock(UUID blocker, UUID target, Instant at) {
        return new Friendship(id, blocker, target, FriendshipStatus.BLOCKED, createdAt, at);
    }
}
