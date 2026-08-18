package fr.projetcompensation.gymbuddy.users;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String handle,
        String passwordHash,
        UserRole role,
        UserStatus status,
        Instant createdAt) {

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public boolean locked() {
        return status == UserStatus.LOCKED;
    }
}
