package fr.projetcompensation.gymbuddy.friends;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository {

    void save(Friendship friendship);

    void update(Friendship friendship);

    void delete(UUID id);

    Optional<Friendship> findById(UUID id);

    Optional<Friendship> findPair(UUID left, UUID right);

    List<Friendship> listAccepted(UUID userId, InstantIdCursor after, int limit);

    List<Friendship> listIncoming(UUID userId, InstantIdCursor after, int limit);

    List<Friendship> listOutgoing(UUID userId, InstantIdCursor after, int limit);

    boolean areAcceptedFriends(UUID left, UUID right);

    int acceptedCount(UUID userId);

    boolean isBlockedEitherWay(UUID left, UUID right);
}
