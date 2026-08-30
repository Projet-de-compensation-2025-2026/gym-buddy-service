package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository {

    void save(Message message);

    void update(Message message);

    Optional<Message> findById(UUID id);

    Optional<Message> findByMediaId(UUID mediaId);

    List<Message> list(UUID conversationId, InstantIdCursor before, int limit);
}
