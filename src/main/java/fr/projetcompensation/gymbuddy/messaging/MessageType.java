package fr.projetcompensation.gymbuddy.messaging;

public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    AUDIO("audio");

    private final String wireValue;

    MessageType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MessageType fromWire(String value) {
        for (MessageType type : values()) {
            if (type.wireValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type");
    }
}
