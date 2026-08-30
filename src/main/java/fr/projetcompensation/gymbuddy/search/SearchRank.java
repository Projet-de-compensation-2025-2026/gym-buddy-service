package fr.projetcompensation.gymbuddy.search;

import java.time.Duration;
import java.time.Instant;

final class SearchRank {

    static final double ALPHA = 0.45;
    static final double BETA = 0.20;
    static final double GAMMA = 0.20;
    static final double DELTA = 0.15;

    private SearchRank() {}

    static double composite(double tsRank, double recency, double geo, double social) {
        return ALPHA * tsRank + BETA * recency + GAMMA * geo + DELTA * social;
    }

    static double peopleRecency(Instant createdAt, Instant now) {
        if (createdAt == null || now == null || createdAt.isAfter(now)) {
            return 1.0;
        }
        double days = Duration.between(createdAt, now).toHours() / 24.0;
        return Math.exp(-days / 90.0);
    }

    static double eventRecency(Instant startsAt, Instant now) {
        if (startsAt == null || now == null || startsAt.isBefore(now)) {
            return 0;
        }
        double hours = Duration.between(now, startsAt).toHours();
        return Math.exp(-hours / (14.0 * 24.0));
    }

    static double geo(Double distanceKm) {
        if (distanceKm == null) {
            return 0;
        }
        return Math.max(0, 1.0 - distanceKm / 50.0);
    }

    static double peopleSocial(boolean friend, boolean friendOfFriend) {
        if (friend) {
            return 1.0;
        }
        if (friendOfFriend) {
            return 0.5;
        }
        return 0;
    }

    static double eventSocial(boolean organizerIsFriend) {
        return organizerIsFriend ? 1.0 : 0;
    }
}
