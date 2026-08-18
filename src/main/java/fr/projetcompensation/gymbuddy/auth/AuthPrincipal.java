package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.UserRole;
import java.util.UUID;

public record AuthPrincipal(UUID userId, String handle, UserRole role) {

    public static final String REQUEST_ATTRIBUTE = "gymBuddy.authPrincipal";
}
