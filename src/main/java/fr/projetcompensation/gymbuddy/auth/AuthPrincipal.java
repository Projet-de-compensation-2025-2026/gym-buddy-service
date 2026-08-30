package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.UserRole;
import java.util.UUID;

public record AuthPrincipal(UUID userId, String handle, UserRole role) {

    public static final String REQUEST_ATTRIBUTE = "gymBuddy.authPrincipal";

    public static AuthPrincipal require(jakarta.servlet.http.HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof AuthPrincipal principal) {
            return principal;
        }
        throw AuthException.unauthenticated("missing or invalid access token");
    }
}
