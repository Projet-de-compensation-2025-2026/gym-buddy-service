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
    static final String EXPIRES_AT = "gymBuddy.expiresAt";

    private final fr.projetcompensation.gymbuddy.users.UserRepository users;
    private final java.time.Clock clock;

    public MessagingSessionRegistry(fr.projetcompensation.gymbuddy.users.UserRepository users, java.time.Clock clock) {
        this.users = users;
        this.clock = clock;
    }

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
                    if (!authorized(userId, session)) {
                        unregister(session);
                        session.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
                        continue;
                    }
                    session.sendMessage(message);
                }
            } catch (IOException ignored) {
                // Dropped sockets never fail the HTTP write.
            }
        }
    }

    private boolean authorized(UUID userId, WebSocketSession session) {
        Object expiration = session.getAttributes().get(EXPIRES_AT);
        if (!(expiration instanceof java.time.Instant expiresAt)
                || !clock.instant().isBefore(expiresAt)
                || users == null) {
            return false;
        }
        try {
            return users.findById(userId)
                    .filter(fr.projetcompensation.gymbuddy.users.User::active)
                    .isPresent();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static UUID userId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID);
        return value instanceof UUID id ? id : null;
    }
}
