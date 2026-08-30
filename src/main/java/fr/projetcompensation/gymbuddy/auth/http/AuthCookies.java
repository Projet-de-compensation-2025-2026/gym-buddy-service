package fr.projetcompensation.gymbuddy.auth.http;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

final class AuthCookies {

    static final String REFRESH = "refresh";
    static final String PATH = "/api/v1/auth";

    private AuthCookies() {}

    static ResponseCookie refresh(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    static ResponseCookie clear() {
        return refresh("", Duration.ZERO);
    }
}
