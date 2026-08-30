package fr.projetcompensation.gymbuddy.search;

public enum FriendStateFilter {
    ANY("any"),
    NOT_FRIENDS("not-friends");

    private final String wireValue;

    FriendStateFilter(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static FriendStateFilter fromWire(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        for (FriendStateFilter filter : values()) {
            if (filter.wireValue.equals(value)) {
                return filter;
            }
        }
        throw new IllegalArgumentException("Unknown friendState");
    }
}
