package fr.projetcompensation.gymbuddy.profiles;

import java.util.UUID;

public interface FriendshipQueries {

    boolean areAcceptedFriends(UUID left, UUID right);

    int acceptedCount(UUID userId);
}
