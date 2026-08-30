package fr.projetcompensation.gymbuddy.events;

public enum EventVisibility {
    PUBLIC("public"),
    FRIENDS("friends"),
    PRIVATE("private");

    private final String wireValue;

    EventVisibility(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static EventVisibility fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unknown event visibility");
        }
        for (EventVisibility visibility : values()) {
            if (visibility.wireValue.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown event visibility");
    }
}
