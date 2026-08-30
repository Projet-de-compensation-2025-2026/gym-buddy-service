package fr.projetcompensation.gymbuddy.fixtures;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Deterministic UUIDs so a second generate without reset is a no-op. */
public final class FixtureIds {

    private FixtureIds() {}

    public static UUID of(long seed, String kind, int index) {
        return UUID.nameUUIDFromBytes(
                ("gym-buddy-fixture|" + seed + "|" + kind + "|" + index).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID demo(String handle) {
        return UUID.nameUUIDFromBytes(("gym-buddy-fixture|demo|" + handle).getBytes(StandardCharsets.UTF_8));
    }
}
