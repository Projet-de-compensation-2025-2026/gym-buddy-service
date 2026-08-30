package fr.projetcompensation.gymbuddy.media.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.media.CreateUpload;
import fr.projetcompensation.gymbuddy.media.MediaService;
import fr.projetcompensation.gymbuddy.media.SignedGet;
import fr.projetcompensation.gymbuddy.openapi.api.MediaApi;
import fr.projetcompensation.gymbuddy.openapi.model.CreateMediaRequest;
import fr.projetcompensation.gymbuddy.openapi.model.CreateMediaResponse;
import fr.projetcompensation.gymbuddy.openapi.model.MediaUrlResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController implements MediaApi {

    private final ObjectProvider<MediaService> media;
    private final HttpServletRequest httpRequest;

    public MediaController(ObjectProvider<MediaService> media, HttpServletRequest httpRequest) {
        this.media = media;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<CreateMediaResponse> postMedia(CreateMediaRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        CreateUpload upload = service()
                .create(
                        principal.userId(),
                        request.getKind().getValue(),
                        request.getMime().getValue(),
                        request.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateMediaResponse(
                        upload.mediaId(),
                        upload.uploadUrl(),
                        OffsetDateTime.ofInstant(upload.expiresAt(), ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<MediaUrlResponse> getMediaIdUrl(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        SignedGet signed = service().url(principal.userId(), id);
        return ResponseEntity.ok(
                new MediaUrlResponse(signed.url(), OffsetDateTime.ofInstant(signed.expiresAt(), ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<Void> deleteMediaId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private MediaService service() {
        MediaService service = media.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("media is not configured");
        }
        return service;
    }
}
