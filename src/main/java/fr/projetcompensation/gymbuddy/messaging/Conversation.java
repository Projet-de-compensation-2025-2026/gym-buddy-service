package fr.projetcompensation.gymbuddy.messaging;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, UUID userLo, UUID userHi, Instant createdAt) {

    public boolean involves(UUID userId) {
        return userLo.equals(userId) || userHi.equals(userId);
    }

    public UUID other(UUID userId) {
        return userLo.equals(userId) ? userHi : userLo;
    }

    static UUID lo(UUID left, UUID right) {
        return left.compareTo(right) < 0 ? left : right;
    }

    static UUID hi(UUID left, UUID right) {
        return left.compareTo(right) < 0 ? right : left;
    }
}
