package fr.projetcompensation.gymbuddy.matching.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.matching.MatchingService;
import fr.projetcompensation.gymbuddy.openapi.api.MatchingApi;
import fr.projetcompensation.gymbuddy.openapi.model.MatchingMe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchingController implements MatchingApi {

    private final ObjectProvider<MatchingService> matching;
    private final HttpServletRequest httpRequest;

    public MatchingController(ObjectProvider<MatchingService> matching, HttpServletRequest httpRequest) {
        this.matching = matching;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<Void> postMatchingOptIn() {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().optIn(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteMatchingOptIn() {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().optOut(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MatchingMe> getMatchingMe() {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(MatchingResponses.toApi(service().me(principal.userId())));
    }

    private MatchingService service() {
        MatchingService service = matching.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("matching is not configured");
        }
        return service;
    }
}
