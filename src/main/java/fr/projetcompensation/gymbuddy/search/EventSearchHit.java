package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.Instant;
import java.util.UUID;

public record EventSearchHit(
        UUID id,
        String title,
        String activity,
        String place,
        Instant startsAt,
        int remainingSeats,
        int capacity,
        User organizer,
        Profile organizerProfile,
        Double distanceKm,
        double rank,
        String matchReason) {}
