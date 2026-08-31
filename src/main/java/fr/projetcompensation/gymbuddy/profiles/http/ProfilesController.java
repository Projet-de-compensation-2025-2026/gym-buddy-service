package fr.projetcompensation.gymbuddy.profiles.http;

import com.fasterxml.jackson.databind.JsonNode;
import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.openapi.api.ProfilesApi;
import fr.projetcompensation.gymbuddy.openapi.model.PatchProfileRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfilesController implements ProfilesApi {

    private final ObjectProvider<ProfileService> profiles;
    private final HttpServletRequest httpRequest;

    public ProfilesController(ObjectProvider<ProfileService> profiles, HttpServletRequest httpRequest) {
        this.profiles = profiles;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<Profile> getProfilesMe() {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(ProfileResponses.toApi(service().me(principal.userId())));
    }

    @Override
    public ResponseEntity<Profile> patchProfilesMe(PatchProfileRequest patchProfileRequest) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        Object raw = httpRequest.getAttribute(PatchProfileBodyAdvice.ATTR);
        JsonNode json = raw instanceof JsonNode node ? node : null;
        return ResponseEntity.ok(ProfileResponses.toApi(
                service().patchMe(principal.userId(), ProfilePatches.fromApi(patchProfileRequest, json))));
    }

    @Override
    public ResponseEntity<Profile> getProfilesHandle(String handle) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(ProfileResponses.toApi(service().byHandle(principal.userId(), handle)));
    }

    private ProfileService service() {
        ProfileService service = profiles.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("profiles are not configured");
        }
        return service;
    }
}
