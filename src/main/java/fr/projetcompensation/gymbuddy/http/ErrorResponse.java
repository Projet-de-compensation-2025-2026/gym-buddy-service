package fr.projetcompensation.gymbuddy.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ErrorDetail> details) {}

    public record ErrorDetail(String path, String issue) {}
}
