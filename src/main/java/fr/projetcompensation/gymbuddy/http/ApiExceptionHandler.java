package fr.projetcompensation.gymbuddy.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorDetail;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponse;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponseError;
import fr.projetcompensation.gymbuddy.openapi.model.ErrorResponseError.CodeEnum;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        };
    }

    private static String issueFor(String code) {
        if (code == null) {
            return "invalid";
        }
        return switch (code) {
            case "NotBlank", "NotNull" -> "required";
            case "Email" -> "format";
            case "Size" -> "size";
            default -> code;
        };
    }
}
