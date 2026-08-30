package fr.projetcompensation.gymbuddy.media;

public enum MediaStatus {
    PENDING("pending"),
    READY("ready"),
    REJECTED("rejected"),
    DELETED("deleted");

    private final String wireValue;

    MediaStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MediaStatus fromWire(String value) {
        for (MediaStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown media status");
    }
}
