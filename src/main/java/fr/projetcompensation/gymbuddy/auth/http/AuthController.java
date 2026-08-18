package fr.projetcompensation.gymbuddy.auth.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthService;
import fr.projetcompensation.gymbuddy.auth.AuthSession;
import fr.projetcompensation.gymbuddy.auth.IssuedTokens;
import fr.projetcompensation.gymbuddy.auth.JwtTokenService;
import fr.projetcompensation.gymbuddy.auth.LoginCommand;
import fr.projetcompensation.gymbuddy.auth.RegisterCommand;
import fr.projetcompensation.gymbuddy.auth.RegisteredUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ObjectProvider<AuthService> authServices;

    public AuthController(ObjectProvider<AuthService> authServices) {
        this.authServices = authServices;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisteredUser user = auth().register(new RegisterCommand(
                request.email(), request.handle(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(
                        user.id(),
                        user.email(),
                        user.handle(),
                        user.displayName(),
                        user.role().wireValue()));
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(auth().login(new LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        return withRefreshCookie(auth().refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        auth().logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clear().toString())
                .build();
    }

    private AuthService auth() {
        AuthService service = authServices.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("auth is not configured");
        }
        return service;
    }

    private static ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthSession session) {
        IssuedTokens tokens = session.tokens();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        AuthCookies.refresh(tokens.refreshToken(), JwtTokenService.REFRESH_TTL)
                                .toString())
                .body(new AccessTokenResponse(
                        tokens.accessToken(), "Bearer", tokens.accessTtl().toSeconds()));
    }
}
