package fr.projetcompensation.gymbuddy.events;

public enum EventApplicationStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    CANCELLED("cancelled"),
    WITHDRAWN("withdrawn");

    private final String wireValue;

    EventApplicationStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static EventApplicationStatus fromWire(String value) {
        for (EventApplicationStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown application status");
    }
}
