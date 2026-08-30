package fr.projetcompensation.gymbuddy.matching;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProposedMatch(
        UUID userA,
        UUID userB,
        double weight,
        String activity,
        Instant startsAt,
        int durationMin,
        LocalDate weekStart,
        UUID eventId) {

    public ProposedMatch withEventId(UUID eventId) {
        return new ProposedMatch(userA, userB, weight, activity, startsAt, durationMin, weekStart, eventId);
    }

    public ProposedMatch withStartsAt(Instant startsAt) {
        return new ProposedMatch(userA, userB, weight, activity, startsAt, durationMin, weekStart, eventId);
    }

    public UUID left() {
        return userA.compareTo(userB) < 0 ? userA : userB;
    }

    public UUID right() {
        return userA.compareTo(userB) < 0 ? userB : userA;
    }
}
