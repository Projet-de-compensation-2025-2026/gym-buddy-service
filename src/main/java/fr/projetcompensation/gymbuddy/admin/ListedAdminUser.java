package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.users.User;

public record ListedAdminUser(User user, String displayName, boolean lastAdmin) {

    InstantIdCursor cursor() {
        return new InstantIdCursor(user.createdAt(), user.id());
    }
}
