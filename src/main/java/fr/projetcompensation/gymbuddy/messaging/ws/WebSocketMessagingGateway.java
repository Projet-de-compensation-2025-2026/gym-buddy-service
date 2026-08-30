package fr.projetcompensation.gymbuddy.messaging.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.projetcompensation.gymbuddy.messaging.ListedConversation;
import fr.projetcompensation.gymbuddy.messaging.Message;
import fr.projetcompensation.gymbuddy.messaging.MessagingGateway;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WebSocketMessagingGateway implements MessagingGateway, AutoCloseable {

    static final String CHANNEL = "gym-buddy:messaging";

    private final MessagingSessionRegistry sessions;
    private final ObjectMapper mapper;
    private final StatefulRedisPubSubConnection<String, String> subscriber;
    private final StatefulRedisPubSubConnection<String, String> publisher;

    public WebSocketMessagingGateway(MessagingSessionRegistry sessions, ObjectMapper mapper, RedisClient redisClient) {
        this.sessions = sessions;
        this.mapper = mapper;
        if (redisClient == null) {
            this.subscriber = null;
            this.publisher = null;
            return;
        }
        this.subscriber = redisClient.connectPubSub();
        this.publisher = redisClient.connectPubSub();
        this.subscriber.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String body) {
                deliver(body);
            }
        });
        this.subscriber.sync().subscribe(CHANNEL);
    }

    @Override
    public void messageCreated(UUID recipientId, Message message) {
        publish(recipientId, "message.created", messagePayload(message));
    }

    @Override
    public void messageDeleted(UUID recipientId, UUID conversationId, UUID messageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", messageId);
        payload.put("conversationId", conversationId);
        publish(recipientId, "message.deleted", payload);
    }

    @Override
    public void conversationUpdated(UUID recipientId, ListedConversation conversation) {
        publish(recipientId, "conversation.updated", conversationPayload(conversation));
    }

    @Override
    public void close() {
        if (subscriber != null) {
            subscriber.close();
        }
        if (publisher != null) {
            publisher.close();
        }
    }

    private void publish(UUID userId, String event, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("userId", userId);
        envelope.put("event", event);
        envelope.put("payload", payload);
        String json;
        try {
            json = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        if (publisher != null) {
            publisher.sync().publish(CHANNEL, json);
            return;
        }
        deliver(json);
    }

    private void deliver(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = mapper.readValue(json, Map.class);
            Object userId = envelope.get("userId");
            if (userId == null) {
                return;
            }
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("event", envelope.get("event"));
            frame.put("payload", envelope.get("payload"));
            sessions.send(UUID.fromString(userId.toString()), mapper.writeValueAsString(frame));
        } catch (JsonProcessingException ignored) {
            // Malformed fan-out is dropped.
        }
    }

    private static Map<String, Object> messagePayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.id());
        payload.put("conversationId", message.conversationId());
        payload.put("senderId", message.senderId());
        payload.put("type", message.type().wireValue());
        payload.put("body", message.visibleBody());
        payload.put("mediaId", message.visibleMediaId());
        payload.put("createdAt", message.createdAt().atOffset(ZoneOffset.UTC).toString());
        payload.put("deleted", message.deleted());
        return payload;
    }

    private static Map<String, Object> conversationPayload(ListedConversation listed) {
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("userId", listed.peer().id());
        peer.put("handle", listed.peer().handle());
        peer.put("displayName", listed.peerProfile().displayName());
        peer.put("avatarMediaId", listed.peerProfile().avatarMediaId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", listed.conversation().id());
        payload.put("peer", peer);
        payload.put("lastMessage", listed.lastMessage() == null ? null : messagePayload(listed.lastMessage()));
        payload.put("unreadCount", listed.unreadCount());
        payload.put("updatedAt", listed.updatedAt().atOffset(ZoneOffset.UTC).toString());
        return payload;
    }
}
