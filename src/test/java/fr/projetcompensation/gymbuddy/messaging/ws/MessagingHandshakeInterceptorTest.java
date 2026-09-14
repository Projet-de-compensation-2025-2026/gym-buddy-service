package fr.projetcompensation.gymbuddy.messaging.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.users.UserRole;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

class MessagingHandshakeInterceptorTest {
    @Test
    void handshakeCarriesVerifiedExpiryAndRejectsMissingExpiry() {
        UUID id = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-09-14T12:15:00Z");
        var request = new MockHttpServletRequest();
        request.setAttribute(AuthPrincipal.REQUEST_ATTRIBUTE, new AuthPrincipal(id, "synthetic", UserRole.MEMBER));
        var attributes = new HashMap<String, Object>();
        var interceptor = new MessagingHandshakeInterceptor();
        assertThat(interceptor.beforeHandshake(
                        new ServletServerHttpRequest(request),
                        mock(ServerHttpResponse.class),
                        mock(WebSocketHandler.class),
                        attributes))
                .isFalse();
        request.setAttribute(AuthPrincipal.EXPIRATION_ATTRIBUTE, expiry);
        assertThat(interceptor.beforeHandshake(
                        new ServletServerHttpRequest(request),
                        mock(ServerHttpResponse.class),
                        mock(WebSocketHandler.class),
                        attributes))
                .isTrue();
        assertThat(attributes)
                .containsEntry(MessagingSessionRegistry.USER_ID, id)
                .containsEntry(MessagingSessionRegistry.EXPIRES_AT, expiry);
    }
}
