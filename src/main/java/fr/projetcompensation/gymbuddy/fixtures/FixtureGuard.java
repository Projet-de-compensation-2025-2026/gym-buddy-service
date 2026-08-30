package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.auth.AuthException;

/** Fixture reset/generate is disabled when the Spring profile is {@code prod}. */
public final class FixtureGuard {

    private FixtureGuard() {}

    public static void requireNonProduction(boolean production) {
        if (production) {
            throw AuthException.forbidden("fixtures are disabled");
        }
    }
}
