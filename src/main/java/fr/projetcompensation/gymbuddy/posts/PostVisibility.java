package fr.projetcompensation.gymbuddy.posts;

public enum PostVisibility {
    FRIENDS("friends"),
    PUBLIC("public");

    private final String wireValue;

    PostVisibility(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static PostVisibility fromWire(String value) {
        if (value == null || value.isBlank()) {
            return FRIENDS;
        }
        for (PostVisibility visibility : values()) {
            if (visibility.wireValue.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown post visibility");
    }
}
