package fr.projetcompensation.gymbuddy.feed.http;

import fr.projetcompensation.gymbuddy.feed.FeedList;
import fr.projetcompensation.gymbuddy.feed.VisibleFeedItem;
import fr.projetcompensation.gymbuddy.openapi.model.FeedItem;
import fr.projetcompensation.gymbuddy.openapi.model.FeedPage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.PostAuthor;
import fr.projetcompensation.gymbuddy.posts.http.PostResponses;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class FeedResponses {

    private FeedResponses() {}

    static FeedPage toPage(FeedList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new FeedPage(list.data().stream().map(FeedResponses::toApi).toList(), page);
    }

    static FeedItem toApi(VisibleFeedItem row) {
        PostAuthor actor = new PostAuthor(
                row.actor().id(), row.actor().handle(), row.actorProfile().displayName());
        actor.setAvatarMediaId(row.actorProfile().avatarMediaId());
        return new FeedItem(
                row.activity().id(),
                FeedItem.KindEnum.fromValue(row.activity().kind().wireValue()),
                actor,
                OffsetDateTime.ofInstant(row.activity().activityAt(), ZoneOffset.UTC),
                PostResponses.toApi(row.post()));
    }
}
