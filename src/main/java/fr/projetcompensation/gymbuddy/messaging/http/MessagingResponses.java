package fr.projetcompensation.gymbuddy.messaging.http;

import fr.projetcompensation.gymbuddy.messaging.ConversationList;
import fr.projetcompensation.gymbuddy.messaging.ListedConversation;
import fr.projetcompensation.gymbuddy.messaging.Message;
import fr.projetcompensation.gymbuddy.messaging.MessageList;
import fr.projetcompensation.gymbuddy.openapi.model.Conversation;
import fr.projetcompensation.gymbuddy.openapi.model.ConversationPage;
import fr.projetcompensation.gymbuddy.openapi.model.ConversationPeer;
import fr.projetcompensation.gymbuddy.openapi.model.MessagePage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class MessagingResponses {

    private MessagingResponses() {}

    static Conversation toApi(ListedConversation listed) {
        ConversationPeer peer = new ConversationPeer(
                listed.peer().id(), listed.peer().handle(), listed.peerProfile().displayName());
        peer.setAvatarMediaId(listed.peerProfile().avatarMediaId());
        Conversation body = new Conversation(
                listed.conversation().id(),
                peer,
                (int) listed.unreadCount(),
                OffsetDateTime.ofInstant(listed.updatedAt(), ZoneOffset.UTC));
        if (listed.lastMessage() != null) {
            body.setLastMessage(toApi(listed.lastMessage()));
        }
        return body;
    }

    static fr.projetcompensation.gymbuddy.openapi.model.Message toApi(Message row) {
        fr.projetcompensation.gymbuddy.openapi.model.Message body =
                new fr.projetcompensation.gymbuddy.openapi.model.Message(
                        row.id(),
                        row.conversationId(),
                        row.senderId(),
                        fr.projetcompensation.gymbuddy.openapi.model.Message.TypeEnum.fromValue(
                                row.type().wireValue()),
                        OffsetDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC),
                        row.deleted());
        body.setBody(row.visibleBody());
        body.setMediaId(row.visibleMediaId());
        return body;
    }

    static ConversationPage toPage(ConversationList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new ConversationPage(
                list.data().stream().map(MessagingResponses::toApi).toList(), page);
    }

    static MessagePage toMessagePage(MessageList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new MessagePage(
                list.data().stream().map(MessagingResponses::toApi).toList(), page);
    }
}
