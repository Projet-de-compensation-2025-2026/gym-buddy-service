package fr.projetcompensation.gymbuddy.messaging.ws;

import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class MessagingHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest http = servletRequest.getServletRequest();
        Object value = http.getAttribute(AuthPrincipal.REQUEST_ATTRIBUTE);
        if (!(value instanceof AuthPrincipal principal)) {
            return false;
        }
        Object expiration = http.getAttribute(AuthPrincipal.EXPIRATION_ATTRIBUTE);
        if (!(expiration instanceof java.time.Instant)) return false;
        attributes.put(MessagingSessionRegistry.USER_ID, principal.userId());
        attributes.put(MessagingSessionRegistry.EXPIRES_AT, expiration);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}
