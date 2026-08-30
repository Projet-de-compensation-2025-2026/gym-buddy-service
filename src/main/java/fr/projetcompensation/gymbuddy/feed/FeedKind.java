package fr.projetcompensation.gymbuddy.feed;

public enum FeedKind {
    POST("post"),
    REPOST("repost");

    private final String wireValue;

    FeedKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static FeedKind fromWire(String value) {
        for (FeedKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown feed kind");
    }
}
