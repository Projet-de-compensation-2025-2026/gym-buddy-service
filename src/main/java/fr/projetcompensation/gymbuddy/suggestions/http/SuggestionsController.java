package fr.projetcompensation.gymbuddy.suggestions.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.openapi.api.SuggestionsApi;
import fr.projetcompensation.gymbuddy.openapi.model.SuggestionPage;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestionsController implements SuggestionsApi {

    private final ObjectProvider<SuggestionService> suggestions;
    private final HttpServletRequest httpRequest;

    public SuggestionsController(ObjectProvider<SuggestionService> suggestions, HttpServletRequest httpRequest) {
        this.suggestions = suggestions;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<SuggestionPage> getSuggestions(Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(SuggestionResponses.toPage(service().list(principal.userId(), size)));
    }

    @Override
    public ResponseEntity<Void> postSuggestionsUserIdDismiss(UUID userId) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().dismiss(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    private SuggestionService service() {
        SuggestionService service = suggestions.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("suggestions are not configured");
        }
        return service;
    }
}
