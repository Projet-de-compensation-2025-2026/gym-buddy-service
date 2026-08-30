package fr.projetcompensation.gymbuddy.media;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CreateUpload(UUID mediaId, URI uploadUrl, Instant expiresAt) {}
