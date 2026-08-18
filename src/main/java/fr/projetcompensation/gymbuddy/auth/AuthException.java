package fr.projetcompensation.gymbuddy.auth;

import java.util.List;

public final class AuthException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldIssue> details;

    public AuthException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public AuthException(ErrorCode code, String message, List<FieldIssue> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldIssue> details() {
        return details;
    }

    public static AuthException validation(String message, FieldIssue issue) {
        return new AuthException(ErrorCode.VALIDATION, message, List.of(issue));
    }

    public static AuthException conflict(String message, FieldIssue issue) {
        return new AuthException(ErrorCode.CONFLICT, message, List.of(issue));
    }

    public static AuthException forbidden(String message) {
        return new AuthException(ErrorCode.FORBIDDEN, message);
    }

    public static AuthException unauthenticated(String message) {
        return new AuthException(ErrorCode.UNAUTHENTICATED, message);
    }
}
