package fr.projetcompensation.gymbuddy.friends.http;

import fr.projetcompensation.gymbuddy.friends.FriendshipList;
import fr.projetcompensation.gymbuddy.friends.ListedFriendship;
import fr.projetcompensation.gymbuddy.openapi.model.Friendship;
import fr.projetcompensation.gymbuddy.openapi.model.FriendshipPage;
import fr.projetcompensation.gymbuddy.openapi.model.FriendshipPeer;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class FriendshipResponses {

    private FriendshipResponses() {}

    static Friendship toApi(ListedFriendship listed) {
        var row = listed.friendship();
        FriendshipPeer peer = new FriendshipPeer(
                listed.peer().id(), listed.peer().handle(), listed.peerProfile().displayName());
        peer.setSports(listed.peerProfile().sports());
        Friendship body = new Friendship(
                row.id(),
                row.requesterId(),
                row.addresseeId(),
                Friendship.StatusEnum.fromValue(row.status().wireValue()),
                OffsetDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC),
                listed.incoming() ? Friendship.DirectionEnum.INCOMING : Friendship.DirectionEnum.OUTGOING,
                peer);
        if (row.respondedAt() != null) {
            body.setRespondedAt(OffsetDateTime.ofInstant(row.respondedAt(), ZoneOffset.UTC));
        }
        return body;
    }

    static FriendshipPage toPage(FriendshipList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new FriendshipPage(
                list.data().stream().map(FriendshipResponses::toApi).toList(), page);
    }
}
