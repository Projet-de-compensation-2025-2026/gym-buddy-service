package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.UUID;

public record Report(
        UUID id,
        UUID reporterId,
        String reporterHandle,
        String targetType,
        UUID targetId,
        String reason,
        String status,
        Instant createdAt) {

    public static final String OPEN = "open";
    public static final String RESOLVED = "resolved";

    boolean open() {
        return OPEN.equals(status);
    }

    Report resolved() {
        return new Report(id, reporterId, reporterHandle, targetType, targetId, reason, RESOLVED, createdAt);
    }

    InstantIdCursor cursor() {
        return new InstantIdCursor(createdAt, id);
    }
}
