package fr.projetcompensation.gymbuddy.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SearchText {

    private SearchText() {}

    static List<String> tokens(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String[] parts = q.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return List.copyOf(tokens);
    }

    static boolean matches(List<String> tokens, String... fields) {
        if (tokens.isEmpty()) {
            return true;
        }
        String haystack = join(fields);
        for (String token : tokens) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    static double tsRank(List<String> tokens, String aFields, String bFields, String cFields) {
        if (tokens.isEmpty()) {
            return 0;
        }
        String a = normalize(aFields);
        String b = normalize(bFields);
        String c = normalize(cFields);
        double weight = 0;
        for (String token : tokens) {
            if (a.contains(token)) {
                weight += 1.0;
            } else if (b.contains(token)) {
                weight += 0.4;
            } else if (c.contains(token)) {
                weight += 0.2;
            }
        }
        return weight / tokens.size();
    }

    static String join(String... fields) {
        StringBuilder out = new StringBuilder();
        for (String field : fields) {
            if (field == null || field.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(field.toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }
}
