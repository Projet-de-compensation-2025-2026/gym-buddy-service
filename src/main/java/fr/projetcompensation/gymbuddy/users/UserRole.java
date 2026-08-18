package fr.projetcompensation.gymbuddy.users;

public enum UserRole {
    MEMBER("member"),
    MODERATOR("moderator"),
    ADMIN("admin");

    private final String wireValue;

    UserRole(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static UserRole fromWire(String value) {
        for (UserRole role : values()) {
            if (role.wireValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role");
    }
}
