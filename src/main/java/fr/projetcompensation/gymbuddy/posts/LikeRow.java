package fr.projetcompensation.gymbuddy.posts;

import java.time.Instant;
import java.util.UUID;

public record LikeRow(UUID userId, Instant likedAt) {}
