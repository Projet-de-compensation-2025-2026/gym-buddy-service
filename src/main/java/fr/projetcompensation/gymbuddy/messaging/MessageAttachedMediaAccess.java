package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.media.AttachedMediaAccess;
import fr.projetcompensation.gymbuddy.media.Media;
import java.util.UUID;

public final class MessageAttachedMediaAccess implements AttachedMediaAccess {

    private final MessageRepository messages;
    private final ConversationRepository conversations;

    public MessageAttachedMediaAccess(MessageRepository messages, ConversationRepository conversations) {
        this.messages = messages;
        this.conversations = conversations;
    }

    @Override
    public boolean canRead(UUID viewerId, Media media) {
        Message message = messages.findByMediaId(media.id()).orElse(null);
        if (message == null) {
            return false;
        }
        Conversation conversation =
                conversations.findById(message.conversationId()).orElse(null);
        return conversation != null && conversation.involves(viewerId);
    }
}
