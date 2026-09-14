package fr.projetcompensation.gymbuddy.messaging.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class MessagingSessionRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);

    @Test
    void activeSessionReceivesMessagesOnlyBeforeVerifiedTokenExpiration() throws Exception {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(1));
        when(users.findById(userId)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        var registry = new MessagingSessionRegistry(users, clock);
        var socket = socket(NOW.plusSeconds(1));
        registry.register(socket);
        registry.send(userId, "before expiry");
        registry.send(userId, "at expiry");
        registry.send(userId, "after expiry");
        verify(socket, times(1)).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void unavailableAccountLookupClosesSocketWithoutFailingPersistedWrite() throws Exception {
        when(users.findById(userId))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        var registry = new MessagingSessionRegistry(users, Clock.fixed(NOW, ZoneOffset.UTC));
        var socket = socket(NOW.plusSeconds(600));
        registry.register(socket);
        registry.send(userId, "private message");
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void lockedAccountSocketIsClosedWithoutDeliveringPrivateMessage() throws Exception {
        when(users.findById(userId)).thenReturn(Optional.of(user(UserStatus.LOCKED)));
        var registry = new MessagingSessionRegistry(users, Clock.fixed(NOW, ZoneOffset.UTC));
        var socket = socket(NOW.plusSeconds(600));
        registry.register(socket);
        registry.send(userId, "private message");
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    private WebSocketSession socket(Instant expiration) {
        var socket = mock(WebSocketSession.class);
        when(socket.getAttributes())
                .thenReturn(Map.of(
                        MessagingSessionRegistry.USER_ID, userId, MessagingSessionRegistry.EXPIRES_AT, expiration));
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

    private User user(UserStatus status) {
        return new User(userId, "synthetic@example.invalid", "synthetic", "hash", UserRole.MEMBER, status, NOW);
    }
}
