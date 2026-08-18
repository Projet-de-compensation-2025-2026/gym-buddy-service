package fr.projetcompensation.gymbuddy.auth.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthService;
import fr.projetcompensation.gymbuddy.auth.AuthSession;
import fr.projetcompensation.gymbuddy.auth.IssuedTokens;
import fr.projetcompensation.gymbuddy.auth.JwtTokenService;
import fr.projetcompensation.gymbuddy.auth.LoginCommand;
import fr.projetcompensation.gymbuddy.auth.RegisterCommand;
import fr.projetcompensation.gymbuddy.openapi.api.AuthApi;
import fr.projetcompensation.gymbuddy.openapi.model.AccessTokenResponse;
import fr.projetcompensation.gymbuddy.openapi.model.LoginRequest;
import fr.projetcompensation.gymbuddy.openapi.model.RegisterRequest;
import fr.projetcompensation.gymbuddy.openapi.model.RegisteredUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final ObjectProvider<AuthService> authServices;
    private final HttpServletRequest httpRequest;

    public AuthController(ObjectProvider<AuthService> authServices, HttpServletRequest httpRequest) {
        this.authServices = authServices;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<RegisteredUser> postAuthRegister(RegisterRequest request) {
        fr.projetcompensation.gymbuddy.auth.RegisteredUser user = auth().register(new RegisterCommand(
                request.getEmail(), request.getHandle(), request.getPassword(), request.getDisplayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(user));
    }

    @Override
    public ResponseEntity<AccessTokenResponse> postAuthLogin(LoginRequest loginRequest) {
        return withRefreshCookie(auth().login(new LoginCommand(loginRequest.getEmail(), loginRequest.getPassword())));
    }

    @Override
    public ResponseEntity<AccessTokenResponse> postAuthRefresh() {
        return withRefreshCookie(auth().refresh(refreshCookie()));
    }

    @Override
    public ResponseEntity<Void> postAuthLogout() {
        auth().logout(refreshCookie());
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

    private String refreshCookie() {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.REFRESH.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthSession session) {
        IssuedTokens tokens = session.tokens();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        AuthCookies.refresh(tokens.refreshToken(), JwtTokenService.REFRESH_TTL)
                                .toString())
                .body(new AccessTokenResponse(tokens.accessToken()));
    }

    private static RegisteredUser toApi(fr.projetcompensation.gymbuddy.auth.RegisteredUser user) {
        return new RegisteredUser(
                user.id(),
                user.email(),
                user.handle(),
                user.displayName(),
                RegisteredUser.RoleEnum.fromValue(user.role().wireValue()));
    }
}
