package fr.projetcompensation.gymbuddy.messaging.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.messaging.MessagingService;
import fr.projetcompensation.gymbuddy.openapi.api.MessagingApi;
import fr.projetcompensation.gymbuddy.openapi.model.Conversation;
import fr.projetcompensation.gymbuddy.openapi.model.ConversationPage;
import fr.projetcompensation.gymbuddy.openapi.model.CreateConversationRequest;
import fr.projetcompensation.gymbuddy.openapi.model.CreateMessageRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Message;
import fr.projetcompensation.gymbuddy.openapi.model.MessagePage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessagingController implements MessagingApi {

    private final ObjectProvider<MessagingService> messaging;
    private final HttpServletRequest httpRequest;

    public MessagingController(ObjectProvider<MessagingService> messaging, HttpServletRequest httpRequest) {
        this.messaging = messaging;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<ConversationPage> getConversations(@Nullable String before, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(MessagingResponses.toPage(service().listInbox(principal.userId(), before, size)));
    }

    @Override
    public ResponseEntity<Conversation> postConversations(
            CreateConversationRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessagingResponses.toApi(service().open(principal.userId(), request.getUserId())));
    }

    @Override
    public ResponseEntity<MessagePage> getConversationsIdMessages(UUID id, @Nullable String before, Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(
                MessagingResponses.toMessagePage(service().listMessages(principal.userId(), id, before, size)));
    }

    @Override
    public ResponseEntity<Message> postConversationsIdMessages(
            UUID id, CreateMessageRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        String type = request.getType() == null ? null : request.getType().getValue();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessagingResponses.toApi(
                        service().send(principal.userId(), id, type, request.getBody(), request.getMediaId())));
    }

    @Override
    public ResponseEntity<Void> deleteMessagesId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> getWs(@Nullable String accessToken) {
        return ResponseEntity.status(HttpStatus.UPGRADE_REQUIRED).build();
    }

    private MessagingService service() {
        MessagingService service = messaging.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("messaging is not configured");
        }
        return service;
    }
}
