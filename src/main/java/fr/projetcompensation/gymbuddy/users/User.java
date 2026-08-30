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

    public boolean closed() {
        return status == UserStatus.CLOSED;
    }

    public boolean blockedFromAuth() {
        return locked() || closed();
    }

    public User withHandle(String handle) {
        return new User(id, email, handle, passwordHash, role, status, createdAt);
    }

    public User withPasswordHash(String passwordHash) {
        return new User(id, email, handle, passwordHash, role, status, createdAt);
    }

    public User withStatus(UserStatus status) {
        return new User(id, email, handle, passwordHash, role, status, createdAt);
    }

    public User withRole(UserRole role) {
        return new User(id, email, handle, passwordHash, role, status, createdAt);
    }

    public boolean isStaff() {
        return role == UserRole.ADMIN || role == UserRole.MODERATOR;
    }
}
