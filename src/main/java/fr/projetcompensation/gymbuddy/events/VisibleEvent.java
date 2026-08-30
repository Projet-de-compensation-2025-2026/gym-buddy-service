package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.util.List;
import java.util.UUID;

public record VisibleEvent(
        Event event,
        User organizer,
        Profile organizerProfile,
        List<VisibleOccurrence> occurrences,
        int remainingSeats,
        VisibleApplication viewerApplication,
        List<VisibleApplicant> pendingApplicants,
        List<UUID> inviteeIds) {}
