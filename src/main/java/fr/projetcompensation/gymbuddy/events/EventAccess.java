package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;

public final class EventAccess {

    private EventAccess() {}

    public static boolean canView(
            Event event, User viewer, FriendshipRepository friendships, UserRepository users, EventRepository events) {
        if (event == null || event.hidden()) {
            return false;
        }
        if (viewer == null || !viewer.active()) {
            return false;
        }
        if (event.organizerId().equals(viewer.id())) {
            return true;
        }
        User organizer = users.findById(event.organizerId()).orElse(null);
        if (organizer == null || !organizer.active()) {
            return false;
        }
        if (friendships.isBlockedEitherWay(viewer.id(), event.organizerId())) {
            return false;
        }
        if (events.hasAccepted(event.id(), viewer.id())) {
            return true;
        }
        return switch (event.visibility()) {
            case PUBLIC -> true;
            case FRIENDS -> friendships.areAcceptedFriends(viewer.id(), event.organizerId());
            case PRIVATE -> events.isInvitee(event.id(), viewer.id());
        };
    }

    public static boolean canApply(
            Event event, User viewer, FriendshipRepository friendships, UserRepository users, EventRepository events) {
        if (!canView(event, viewer, friendships, users, events)) {
            return false;
        }
        return !event.organizerId().equals(viewer.id());
    }
}
