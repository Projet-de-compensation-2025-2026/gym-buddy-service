package fr.projetcompensation.gymbuddy.messaging;

import java.util.UUID;

public interface MessagingGateway {

    void messageCreated(UUID recipientId, Message message);

    void messageDeleted(UUID recipientId, UUID conversationId, UUID messageId);

    void conversationUpdated(UUID recipientId, ListedConversation conversation);

    static MessagingGateway noop() {
        return new MessagingGateway() {
            @Override
            public void messageCreated(UUID recipientId, Message message) {}

            @Override
            public void messageDeleted(UUID recipientId, UUID conversationId, UUID messageId) {}

            @Override
            public void conversationUpdated(UUID recipientId, ListedConversation conversation) {}
        };
    }
}
