package fr.projetcompensation.gymbuddy.profiles.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.projetcompensation.gymbuddy.openapi.model.PatchProfileRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Keeps the raw PATCH JSON so omitted keys are distinct from empty arrays and JSON null. */
@ControllerAdvice
public class PatchProfileBodyAdvice extends RequestBodyAdviceAdapter {

    static final String ATTR = "gymBuddy.patchProfileJson";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return PatchProfileRequest.class.equals(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        byte[] body = inputMessage.getBody().readAllBytes();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(ATTR, MAPPER.readTree(body), RequestAttributes.SCOPE_REQUEST);
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }
        };
    }
}
