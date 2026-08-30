package fr.projetcompensation.gymbuddy.friends;

public enum FriendshipStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    BLOCKED("blocked");

    private final String wireValue;

    FriendshipStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static FriendshipStatus fromWire(String value) {
        for (FriendshipStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown friendship status");
    }
}
