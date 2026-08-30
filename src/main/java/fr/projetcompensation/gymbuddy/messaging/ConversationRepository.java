package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {

    void save(Conversation conversation);

    Optional<Conversation> findById(UUID id);

    Optional<Conversation> findPair(UUID left, UUID right);

    List<InboxRow> listInbox(UUID userId, InstantIdCursor before, int limit);

    Optional<InboxRow> inboxRow(UUID conversationId, UUID userId);

    void markRead(UUID conversationId, UUID userId, Instant at);
}
