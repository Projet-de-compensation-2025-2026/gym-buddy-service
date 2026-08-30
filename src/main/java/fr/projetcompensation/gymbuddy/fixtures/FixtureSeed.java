package fr.projetcompensation.gymbuddy.fixtures;

/** Approved Datafaker seed (`FIXTURE_SEED=20260813`). */
public final class FixtureSeed {

    public static final long DEFAULT = 20260813L;

    private FixtureSeed() {}

    public static long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT;
        }
    }
}
