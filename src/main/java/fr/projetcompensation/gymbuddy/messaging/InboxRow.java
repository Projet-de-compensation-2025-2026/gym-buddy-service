package fr.projetcompensation.gymbuddy.messaging;

import java.time.Instant;

public record InboxRow(Conversation conversation, Message lastMessage, long unreadCount) {

    Instant updatedAt() {
        return lastMessage == null ? conversation.createdAt() : lastMessage.createdAt();
    }
}
