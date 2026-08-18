package fr.projetcompensation.gymbuddy.users;

public enum UserStatus {
    ACTIVE("active"),
    LOCKED("locked"),
    PENDING_VERIFICATION("pending_verification");

    private final String wireValue;

    UserStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static UserStatus fromWire(String value) {
        for (UserStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status");
    }
}
