package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.AttachedMediaAccess;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.util.UUID;

public final class EventAttachedMediaAccess implements AttachedMediaAccess {

    private final EventRepository events;
    private final FriendshipRepository friendships;
    private final UserRepository users;

    public EventAttachedMediaAccess(EventRepository events, FriendshipRepository friendships, UserRepository users) {
        this.events = events;
        this.friendships = friendships;
        this.users = users;
    }

    @Override
    public boolean canRead(UUID viewerId, Media media) {
        Event event = events.findByCoverMediaId(media.id()).orElse(null);
        if (event == null) {
            return false;
        }
        User viewer = users.findById(viewerId).orElse(null);
        return EventAccess.canView(event, viewer, friendships, users, events);
    }
}
