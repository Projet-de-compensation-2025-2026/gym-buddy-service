package fr.projetcompensation.gymbuddy.comments.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.comments.CommentService;
import fr.projetcompensation.gymbuddy.openapi.api.CommentsApi;
import fr.projetcompensation.gymbuddy.openapi.model.Comment;
import fr.projetcompensation.gymbuddy.openapi.model.CommentPage;
import fr.projetcompensation.gymbuddy.openapi.model.CreateCommentRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentsController implements CommentsApi {

    private final ObjectProvider<CommentService> comments;
    private final HttpServletRequest httpRequest;

    public CommentsController(ObjectProvider<CommentService> comments, HttpServletRequest httpRequest) {
        this.comments = comments;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<CommentPage> getPostsIdComments(UUID id, @Nullable String before, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(CommentResponses.toPage(service().listRoots(principal.userId(), id, before, size)));
    }

    @Override
    public ResponseEntity<Comment> postPostsIdComments(
            UUID id, CreateCommentRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponses.toApi(
                        service().create(principal.userId(), id, request.getBody(), request.getParentId())));
    }

    @Override
    public ResponseEntity<CommentPage> getCommentsIdReplies(UUID id, @Nullable String before, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(CommentResponses.toPage(service().listReplies(principal.userId(), id, before, size)));
    }

    @Override
    public ResponseEntity<Void> deleteCommentsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> putCommentsIdLike(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().like(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteCommentsIdLike(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().unlike(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private CommentService service() {
        CommentService service = comments.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("comments are not configured");
        }
        return service;
    }
}
