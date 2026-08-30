package fr.projetcompensation.gymbuddy.messaging.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MessagingWebSocketHandler extends TextWebSocketHandler {

    private final MessagingSessionRegistry sessions;

    public MessagingWebSocketHandler(MessagingSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.unregister(session);
    }
}
