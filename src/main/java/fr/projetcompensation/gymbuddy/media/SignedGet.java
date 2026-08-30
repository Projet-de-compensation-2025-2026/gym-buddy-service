package fr.projetcompensation.gymbuddy.media;

import java.net.URI;
import java.time.Instant;

public record SignedGet(URI url, Instant expiresAt) {}
