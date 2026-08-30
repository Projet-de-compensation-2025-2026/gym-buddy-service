package fr.projetcompensation.gymbuddy.profiles;

public enum ExperienceLevel {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced");

    private final String wireValue;

    ExperienceLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ExperienceLevel fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ExperienceLevel level : values()) {
            if (level.wireValue.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown experience");
    }
}
