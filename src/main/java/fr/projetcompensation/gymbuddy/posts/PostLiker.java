package fr.projetcompensation.gymbuddy.posts;

import java.time.Instant;
import java.util.UUID;

public record PostLiker(UUID userId, String handle, String displayName, UUID avatarMediaId, Instant likedAt) {}
