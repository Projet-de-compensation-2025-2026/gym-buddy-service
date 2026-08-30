package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;

public record PeopleSearchHit(
        User user,
        Profile profile,
        Double distanceKm,
        HitFriendState friendState,
        double rank,
        String matchReason) {}
