package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;

public final class ExperienceCloseness {

    private ExperienceCloseness() {}

    public static double feature(ExperienceLevel left, ExperienceLevel right) {
        if (left == null || right == null) {
            return 0.0;
        }
        if (left == right) {
            return 1.0;
        }
        return adjacent(left, right) ? 0.5 : 0.0;
    }

    private static boolean adjacent(ExperienceLevel left, ExperienceLevel right) {
        return (left == ExperienceLevel.BEGINNER && right == ExperienceLevel.INTERMEDIATE)
                || (left == ExperienceLevel.INTERMEDIATE && right == ExperienceLevel.BEGINNER)
                || (left == ExperienceLevel.INTERMEDIATE && right == ExperienceLevel.ADVANCED)
                || (left == ExperienceLevel.ADVANCED && right == ExperienceLevel.INTERMEDIATE);
    }
}
