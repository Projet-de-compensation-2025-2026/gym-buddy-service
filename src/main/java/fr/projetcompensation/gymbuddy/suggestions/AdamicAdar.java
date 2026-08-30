package fr.projetcompensation.gymbuddy.suggestions;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

public final class AdamicAdar {

    private AdamicAdar() {}

    public static double raw(Set<UUID> mutual, ToIntFunction<UUID> degree) {
        double sum = 0.0;
        for (UUID friend : mutual) {
            int deg = Math.max(0, degree.applyAsInt(friend));
            double denom = Math.log(1.0 + deg);
            if (denom > 0.0) {
                sum += 1.0 / denom;
            }
        }
        return sum;
    }

    public static double minMax(double value, double min, double max) {
        if (max <= min) {
            return value > 0.0 ? 1.0 : 0.0;
        }
        return (value - min) / (max - min);
    }

    public static Set<UUID> mutual(Collection<UUID> left, Collection<UUID> right) {
        return left.stream().filter(right::contains).collect(java.util.stream.Collectors.toSet());
    }

    public static int degree(UUID userId, Map<UUID, Set<UUID>> neighbors) {
        Set<UUID> set = neighbors.get(userId);
        return set == null ? 0 : set.size();
    }
}
