package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.User;
import java.util.Optional;

public interface TokenService {

    IssuedTokens issue(User user);

    Optional<AccessClaims> parseAccess(String token);

    Optional<RefreshClaims> parseRefresh(String token);
}
