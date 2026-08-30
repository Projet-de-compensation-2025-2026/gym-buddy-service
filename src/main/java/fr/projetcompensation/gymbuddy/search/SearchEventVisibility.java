package fr.projetcompensation.gymbuddy.search;

public enum SearchEventVisibility {
    PUBLIC("public"),
    FRIENDS("friends"),
    PRIVATE("private");

    private final String wireValue;

    SearchEventVisibility(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static SearchEventVisibility fromWire(String value) {
        for (SearchEventVisibility visibility : values()) {
            if (visibility.wireValue.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown event visibility");
    }
}
