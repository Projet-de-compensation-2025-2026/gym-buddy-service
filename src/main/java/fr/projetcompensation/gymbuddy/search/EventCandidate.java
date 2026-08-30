package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EventCandidate(
        UUID id,
        User organizer,
        Profile organizerProfile,
        String title,
        String description,
        String activity,
        String place,
        Double lat,
        Double lng,
        Instant startsAt,
        int remainingSeats,
        int capacity,
        SearchEventVisibility visibility,
        Set<UUID> inviteeIds,
        Set<UUID> acceptedApplicantIds,
        boolean hidden,
        boolean cancelled) {

    public EventCandidate {
        inviteeIds = inviteeIds == null ? Set.of() : Set.copyOf(inviteeIds);
        acceptedApplicantIds = acceptedApplicantIds == null ? Set.of() : Set.copyOf(acceptedApplicantIds);
    }
}
