package fr.projetcompensation.gymbuddy.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorDetail;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponse;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponseError;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponseError.CodeEnum;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        return envelope(
                ex.code(),
                ex.getMessage(),
                ex.details().stream()
                        .map(issue -> new ErrorDetail(issue.path(), issue.issue()))
                        .toList());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), issueFor(error.getCode())))
                .toList();
        return envelope(ErrorCode.VALIDATION, "request is not valid", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable() {
        return envelope(ErrorCode.VALIDATION, "request is not valid", List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation ->
                        new ErrorDetail(pathOf(violation.getPropertyPath().toString()), "range"))
                .toList();
        return envelope(ErrorCode.VALIDATION, "request is not valid", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        return envelope(ErrorCode.VALIDATION, "request is not valid", List.of());
    }

    private static ResponseEntity<ErrorResponse> envelope(ErrorCode code, String message, List<ErrorDetail> details) {
        ErrorResponseError error = new ErrorResponseError(CodeEnum.fromValue(code.name()), message);
        error.setDetails(details.isEmpty() ? null : details);
        return ResponseEntity.status(statusOf(code)).body(new ErrorResponse(error));
    }

    private static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case QUOTA_EXCEEDED -> HttpStatus.CONFLICT;
        };
    }

    private static String pathOf(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "value";
        }
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    private static String issueFor(String code) {
        if (code == null) {
            return "invalid";
        }
        return switch (code) {
            case "NotBlank", "NotNull" -> "required";
            case "Email", "Pattern" -> "format";
            case "Size" -> "size";
            default -> code;
        };
    }
}
