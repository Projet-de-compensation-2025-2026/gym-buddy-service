package fr.projetcompensation.gymbuddy.friends;

public enum FriendshipFilter {
    ACCEPTED,
    INCOMING,
    OUTGOING,
    BLOCKED;

    public static FriendshipFilter fromQuery(String value) {
        if (value == null || value.isBlank() || "accepted".equalsIgnoreCase(value)) {
            return ACCEPTED;
        }
        if ("incoming".equalsIgnoreCase(value)) {
            return INCOMING;
        }
        if ("outgoing".equalsIgnoreCase(value)) {
            return OUTGOING;
        }
        if ("blocked".equalsIgnoreCase(value)) {
            return BLOCKED;
        }
        throw new IllegalArgumentException(value);
    }
}
