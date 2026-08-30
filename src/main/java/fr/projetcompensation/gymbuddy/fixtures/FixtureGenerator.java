package fr.projetcompensation.gymbuddy.fixtures;

import java.util.UUID;

public interface FixtureGenerator {

    FixtureReport generate(FixtureMagnitude magnitude);

    void reset(UUID preserveUserId);
}
