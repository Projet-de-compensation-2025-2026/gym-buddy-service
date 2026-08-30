package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.UserRole;
import java.util.UUID;

public record RegisteredUser(UUID id, String email, String handle, String displayName, UserRole role) {}
