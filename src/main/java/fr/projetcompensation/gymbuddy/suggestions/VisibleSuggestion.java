package fr.projetcompensation.gymbuddy.suggestions;

public record VisibleSuggestion(MemberSnapshot candidate, ScoredCandidate scored, boolean stub) {

    public String displayName() {
        if (!stub) {
            return candidate.displayName();
        }
        return initials(candidate.displayName(), candidate.handle());
    }

    public String city() {
        return stub ? null : candidate.city();
    }

    static String initials(String displayName, String handle) {
        String source = displayName == null || displayName.isBlank() ? handle : displayName.trim();
        String[] parts = source.split("\\s+");
        if (parts.length == 1) {
            String token = parts[0];
            return token.length() <= 2
                    ? token.toUpperCase()
                    : token.substring(0, 1).toUpperCase() + ".";
        }
        String first = parts[0];
        String last = parts[parts.length - 1];
        char initial = last.isEmpty() ? '?' : Character.toUpperCase(last.charAt(0));
        return first + " " + initial + ".";
    }
}
