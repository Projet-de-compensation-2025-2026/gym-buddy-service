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
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenFilter extends OncePerRequestFilter {

    static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/healthz",
            "/api/v1/readyz",
            "/healthz",
            "/readyz");

    private final ObjectProvider<TokenService> tokens;
    private final ObjectProvider<UserRepository> users;

    public AccessTokenFilter(ObjectProvider<TokenService> tokens, ObjectProvider<UserRepository> users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = publicPath(request);
        return PUBLIC_PATHS.contains(path) || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
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

    private static String publicPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        int semicolon = uri.indexOf(';');
        if (semicolon >= 0) {
            uri = uri.substring(0, semicolon);
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
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
