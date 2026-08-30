package fr.projetcompensation.gymbuddy.posts.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.openapi.api.PostsApi;
import fr.projetcompensation.gymbuddy.openapi.model.CreatePostRequest;
import fr.projetcompensation.gymbuddy.openapi.model.PatchPostRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Post;
import fr.projetcompensation.gymbuddy.openapi.model.PostLikerPage;
import fr.projetcompensation.gymbuddy.posts.PostService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostsController implements PostsApi {

    private final ObjectProvider<PostService> posts;
    private final HttpServletRequest httpRequest;

    public PostsController(ObjectProvider<PostService> posts, HttpServletRequest httpRequest) {
        this.posts = posts;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<Post> postPosts(CreatePostRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        String visibility =
                request.getVisibility() == null ? null : request.getVisibility().getValue();
        List<UUID> mediaIds = request.getMediaIds() == null ? List.of() : List.copyOf(request.getMediaIds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostResponses.toApi(
                        service().create(principal.userId(), request.getBody(), visibility, mediaIds)));
    }

    @Override
    public ResponseEntity<Post> getPostsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(PostResponses.toApi(service().get(principal.userId(), id)));
    }

    @Override
    public ResponseEntity<Post> patchPostsId(UUID id, PatchPostRequest request) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(PostResponses.toApi(service().edit(principal.userId(), id, request.getBody())));
    }

    @Override
    public ResponseEntity<Void> deletePostsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Post> postPostsIdReposts(UUID id, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostResponses.toApi(service().repost(principal.userId(), id)));
    }

    @Override
    public ResponseEntity<Void> deletePostsIdReposts(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().unrepost(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> putPostsIdLike(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().like(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deletePostsIdLike(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().unlike(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PostLikerPage> getPostsIdLikes(UUID id, @Nullable String after, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(PostResponses.toPage(service().likes(principal.userId(), id, after, size)));
    }

    private PostService service() {
        PostService service = posts.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("posts are not configured");
        }
        return service;
    }
}
