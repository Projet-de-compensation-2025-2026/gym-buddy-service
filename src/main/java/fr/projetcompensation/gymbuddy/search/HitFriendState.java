package fr.projetcompensation.gymbuddy.search;

public enum HitFriendState {
    FRIENDS("friends"),
    PENDING("pending"),
    NONE("none");

    private final String wireValue;

    HitFriendState(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
