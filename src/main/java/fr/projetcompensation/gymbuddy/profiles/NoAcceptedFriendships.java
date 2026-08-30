package fr.projetcompensation.gymbuddy.profiles;

import java.util.UUID;

/** Fallback when no friendship rows exist (tests without JDBC). */
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
