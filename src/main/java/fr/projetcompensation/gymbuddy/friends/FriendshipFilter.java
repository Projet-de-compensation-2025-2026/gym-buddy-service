package fr.projetcompensation.gymbuddy.friends;

public enum FriendshipFilter {
    ACCEPTED,
    INCOMING,
    OUTGOING;

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
        throw new IllegalArgumentException(value);
    }
}
