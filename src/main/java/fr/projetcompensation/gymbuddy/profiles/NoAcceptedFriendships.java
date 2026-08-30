package fr.projetcompensation.gymbuddy.profiles;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Ticket #60 owns the friendship table. Until then, nobody is an accepted friend. */
@Component
public final class NoAcceptedFriendships implements FriendshipQueries {

    @Override
    public boolean areAcceptedFriends(UUID left, UUID right) {
        return false;
    }

    @Override
    public int acceptedCount(UUID userId) {
        return 0;
    }
}
