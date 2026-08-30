package fr.projetcompensation.gymbuddy.profiles;

public enum ProfileVisibility {
    PUBLIC("public"),
    PRIVATE("private");

    private final String wireValue;

    ProfileVisibility(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ProfileVisibility fromWire(String value) {
        for (ProfileVisibility visibility : values()) {
            if (visibility.wireValue.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown visibility");
    }
}
