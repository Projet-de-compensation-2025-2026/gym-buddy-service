package fr.projetcompensation.gymbuddy.media;

public enum MediaKind {
    AVATAR("avatar"),
    POST("post"),
    MESSAGE("message"),
    EVENT("event");

    private final String wireValue;

    MediaKind(String wireValue) {
        this.wireValue = wireValue;
    }

    String wireValue() {
        return wireValue;
    }

    static MediaKind fromWire(String value) {
        for (MediaKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown media kind");
    }

    boolean imageOnly() {
        return this == AVATAR || this == POST || this == EVENT;
    }
}
