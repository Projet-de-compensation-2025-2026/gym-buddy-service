package fr.projetcompensation.gymbuddy.messaging.ws;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public final class MessagingSessionRegistry {

    static final String USER_ID = "gymBuddy.userId";

    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        UUID userId = userId(session);
        if (userId == null) {
            return;
        }
        sessions.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(WebSocketSession session) {
        UUID userId = userId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> open = sessions.get(userId);
        if (open != null) {
            open.remove(session);
            if (open.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public void send(UUID userId, String payload) {
        Set<WebSocketSession> open = sessions.get(userId);
        if (open == null || open.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : open) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException ignored) {
                // Dropped sockets never fail the HTTP write.
            }
        }
    }

    static UUID userId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID);
        return value instanceof UUID id ? id : null;
    }
}
