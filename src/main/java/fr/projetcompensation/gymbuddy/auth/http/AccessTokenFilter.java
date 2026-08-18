package fr.projetcompensation.gymbuddy.auth.http;

import fr.projetcompensation.gymbuddy.auth.AccessClaims;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.auth.TokenService;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenFilter extends OncePerRequestFilter {

    private final ObjectProvider<TokenService> tokens;
    private final ObjectProvider<UserRepository> users;

    public AccessTokenFilter(ObjectProvider<TokenService> tokens, ObjectProvider<UserRepository> users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> bearer = bearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (bearer.isEmpty()) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing or invalid access token");
            return;
        }
        TokenService tokenService = tokens.getIfAvailable();
        UserRepository userRepository = users.getIfAvailable();
        if (tokenService == null || userRepository == null) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing or invalid access token");
            return;
        }
        Optional<AccessClaims> claims = tokenService.parseAccess(bearer.get());
        if (claims.isEmpty()) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing or invalid access token");
            return;
        }
        Optional<User> user = userRepository.findById(claims.get().userId());
        if (user.isEmpty() || !user.get().active()) {
            if (user.isPresent() && user.get().locked()) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "account is locked");
                return;
            }
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing or invalid access token");
            return;
        }
        request.setAttribute(
                AuthPrincipal.REQUEST_ATTRIBUTE,
                new AuthPrincipal(
                        user.get().id(), user.get().handle(), user.get().role()));
        filterChain.doFilter(request, response);
    }

    private static boolean isPublic(String path) {
        return "/api/v1/healthz".equals(path)
                || "/api/v1/readyz".equals(path)
                || path.startsWith("/api/v1/auth/")
                || !path.startsWith("/api/v1/");
    }

    private static Optional<String> bearer(String header) {
        if (header == null || header.length() < 8) {
            return Optional.empty();
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"error\":{\"code\":\"" + code.name() + "\",\"message\":\"" + escape(message) + "\"}}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
