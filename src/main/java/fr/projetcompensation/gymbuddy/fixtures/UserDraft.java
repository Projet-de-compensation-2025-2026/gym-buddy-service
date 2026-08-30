package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.util.UUID;

public record UserDraft(User user, Profile profile, int clusterIndex) {

    public UUID id() {
        return user.id();
    }

    public String handle() {
        return user.handle();
    }

    public String city() {
        return profile.city();
    }

    public String sport() {
        return profile.sports().isEmpty() ? "" : profile.sports().getFirst();
    }
}
