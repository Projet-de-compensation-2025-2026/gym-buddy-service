package fr.projetcompensation.gymbuddy.search.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.openapi.api.SearchApi;
import fr.projetcompensation.gymbuddy.openapi.model.EventSearchPage;
import fr.projetcompensation.gymbuddy.openapi.model.PeopleSearchPage;
import fr.projetcompensation.gymbuddy.search.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController implements SearchApi {

    private final ObjectProvider<SearchService> search;
    private final HttpServletRequest httpRequest;

    public SearchController(ObjectProvider<SearchService> search, HttpServletRequest httpRequest) {
        this.search = search;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<PeopleSearchPage> getSearchPeople(
            @Nullable String q,
            @Nullable List<String> sports,
            @Nullable String experience,
            @Nullable String city,
            @Nullable Integer radiusKm,
            @Nullable String friendState,
            @Nullable String sort,
            @Nullable Boolean debug,
            @Nullable String before,
            Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(SearchResponses.toPeoplePage(service()
                .searchPeople(
                        principal.userId(),
                        q,
                        sports,
                        experience,
                        city,
                        radiusKm,
                        friendState,
                        sort,
                        debug,
                        before,
                        size)));
    }

    @Override
    public ResponseEntity<EventSearchPage> getSearchEvents(
            @Nullable String q,
            @Nullable String activity,
            @Nullable OffsetDateTime from,
            @Nullable OffsetDateTime to,
            @Nullable Boolean remaining,
            @Nullable Integer radiusKm,
            @Nullable Boolean organizerIsFriend,
            @Nullable String sort,
            @Nullable Boolean debug,
            @Nullable String before,
            Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(SearchResponses.toEventPage(service()
                .searchEvents(
                        principal.userId(),
                        q,
                        activity,
                        instant(from),
                        instant(to),
                        remaining,
                        radiusKm,
                        organizerIsFriend,
                        sort,
                        debug,
                        before,
                        size)));
    }

    private SearchService service() {
        SearchService service = search.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("search is not configured");
        }
        return service;
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
