package fr.projetcompensation.gymbuddy.feed;

import java.time.Instant;
import java.util.UUID;

public record FeedActivity(UUID id, FeedKind kind, UUID actorId, UUID postId, Instant activityAt) {}
