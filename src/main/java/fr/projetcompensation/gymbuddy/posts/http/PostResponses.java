package fr.projetcompensation.gymbuddy.posts.http;

import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.Post;
import fr.projetcompensation.gymbuddy.openapi.model.PostAuthor;
import fr.projetcompensation.gymbuddy.openapi.model.PostLiker;
import fr.projetcompensation.gymbuddy.openapi.model.PostLikerPage;
import fr.projetcompensation.gymbuddy.posts.PostLikerList;
import fr.projetcompensation.gymbuddy.posts.VisiblePost;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class PostResponses {

    private PostResponses() {}

    public static Post toApi(VisiblePost row) {
        PostAuthor author = new PostAuthor(
                row.author().id(), row.author().handle(), row.authorProfile().displayName());
        author.setAvatarMediaId(row.authorProfile().avatarMediaId());
        Post body = new Post(
                row.post().id(),
                author,
                Post.VisibilityEnum.fromValue(row.post().visibility().wireValue()),
                row.mediaIds(),
                OffsetDateTime.ofInstant(row.post().createdAt(), ZoneOffset.UTC),
                (int) row.likeCount(),
                (int) row.repostCount(),
                (int) row.commentCount(),
                row.liked(),
                row.reposted());
        body.setBody(row.post().body());
        if (row.post().editedAt() != null) {
            body.setEditedAt(OffsetDateTime.ofInstant(row.post().editedAt(), ZoneOffset.UTC));
        }
        return body;
    }

    static PostLikerPage toPage(PostLikerList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new PostLikerPage(
                list.data().stream()
                        .map(row -> {
                            PostLiker liker = new PostLiker(row.userId(), row.handle(), row.displayName());
                            liker.setAvatarMediaId(row.avatarMediaId());
                            liker.setLikedAt(OffsetDateTime.ofInstant(row.likedAt(), ZoneOffset.UTC));
                            return liker;
                        })
                        .toList(),
                page);
    }
}
