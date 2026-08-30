package fr.projetcompensation.gymbuddy.feed.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.feed.FeedService;
import fr.projetcompensation.gymbuddy.openapi.api.FeedApi;
import fr.projetcompensation.gymbuddy.openapi.model.FeedPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController implements FeedApi {

    private final ObjectProvider<FeedService> feed;
    private final HttpServletRequest httpRequest;

    public FeedController(ObjectProvider<FeedService> feed, HttpServletRequest httpRequest) {
        this.feed = feed;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<FeedPage> getFeed(@Nullable String before, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(FeedResponses.toPage(service().list(principal.userId(), before, size)));
    }

    private FeedService service() {
        FeedService service = feed.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("feed is not configured");
        }
        return service;
    }
}
