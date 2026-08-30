package fr.projetcompensation.gymbuddy.friends.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.friends.FriendshipService;
import fr.projetcompensation.gymbuddy.openapi.api.FriendsApi;
import fr.projetcompensation.gymbuddy.openapi.model.CreateBlockRequest;
import fr.projetcompensation.gymbuddy.openapi.model.CreateFriendshipRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Friendship;
import fr.projetcompensation.gymbuddy.openapi.model.FriendshipPage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FriendsController implements FriendsApi {

    private final ObjectProvider<FriendshipService> friendships;
    private final HttpServletRequest httpRequest;

    public FriendsController(ObjectProvider<FriendshipService> friendships, HttpServletRequest httpRequest) {
        this.friendships = friendships;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<FriendshipPage> getFriendships(
            String filter, @Nullable String handle, @Nullable String after, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(
                FriendshipResponses.toPage(service().list(principal.userId(), filter, handle, after, size)));
    }

    @Override
    public ResponseEntity<Friendship> postFriendships(
            CreateFriendshipRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FriendshipResponses.toApi(
                        service().request(principal.userId(), request.getHandle(), request.getUserId())));
    }

    @Override
    public ResponseEntity<Friendship> postFriendshipsIdAccept(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(FriendshipResponses.toApi(service().accept(principal.userId(), id)));
    }

    @Override
    public ResponseEntity<Void> postFriendshipsIdDecline(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().decline(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteFriendshipsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().remove(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postBlocks(CreateBlockRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().block(principal.userId(), request.getUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteBlocksUserId(UUID userId) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().unblock(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    private FriendshipService service() {
        FriendshipService service = friendships.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("friends are not configured");
        }
        return service;
    }
}
