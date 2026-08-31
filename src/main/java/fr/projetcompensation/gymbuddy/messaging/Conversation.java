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

    /**
     * PostgreSQL {@code uuid <} is unsigned. Java {@code UUID.compareTo} is signed, so a pair
     * whose most-significant bit differs would violate {@code conversations_pair_order}.
     */
    public static UUID lo(UUID left, UUID right) {
        return unsignedLess(left, right) ? left : right;
    }

    public static UUID hi(UUID left, UUID right) {
        return unsignedLess(left, right) ? right : left;
    }

    static boolean unsignedLess(UUID left, UUID right) {
        int cmp = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        if (cmp == 0) {
            cmp = Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
        }
        return cmp < 0;
    }
}
