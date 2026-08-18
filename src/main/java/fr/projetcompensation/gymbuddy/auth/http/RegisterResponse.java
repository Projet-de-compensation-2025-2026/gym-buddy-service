package fr.projetcompensation.gymbuddy.auth.http;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, String handle, String displayName, String role) {}
