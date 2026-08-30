package fr.projetcompensation.gymbuddy.suggestions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SportsOverlap {

    private SportsOverlap() {}

    public static double jaccard(Collection<String> left, Collection<String> right) {
        Set<String> a = normalize(left);
        Set<String> b = normalize(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String sport : a) {
            if (b.contains(sport)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    public static List<String> sharedInOrder(Collection<String> viewer, Collection<String> other) {
        Set<String> theirs = normalize(other);
        List<String> shared = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String sport : viewer == null ? List.<String>of() : viewer) {
            if (sport == null || sport.isBlank()) {
                continue;
            }
            String key = sport.trim().toLowerCase(Locale.ROOT);
            if (theirs.contains(key) && seen.add(key)) {
                shared.add(sport.trim());
            }
        }
        return List.copyOf(shared);
    }

    public static boolean sharesAny(Collection<String> left, Collection<String> right) {
        return !sharedInOrder(left, right).isEmpty();
    }

    static Set<String> normalize(Collection<String> sports) {
        Set<String> keys = new LinkedHashSet<>();
        if (sports == null) {
            return keys;
        }
        for (String sport : sports) {
            if (sport == null || sport.isBlank()) {
                continue;
            }
            keys.add(sport.trim().toLowerCase(Locale.ROOT));
        }
        return keys;
    }
}
