package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.UUID;

public record ListedAdminContent(
        String type,
        UUID id,
        String authorHandle,
        String summary,
        Instant createdAt,
        boolean hidden,
        String hiddenReason) {

    InstantIdCursor cursor() {
        return new InstantIdCursor(createdAt, id);
    }
}
