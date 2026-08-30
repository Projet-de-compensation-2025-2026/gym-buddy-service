package fr.projetcompensation.gymbuddy.comments.http;

import fr.projetcompensation.gymbuddy.comments.CommentList;
import fr.projetcompensation.gymbuddy.comments.VisibleComment;
import fr.projetcompensation.gymbuddy.openapi.model.Comment;
import fr.projetcompensation.gymbuddy.openapi.model.CommentPage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.PostAuthor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class CommentResponses {

    private CommentResponses() {}

    static Comment toApi(VisibleComment row) {
        PostAuthor author = new PostAuthor(
                row.author().id(), row.author().handle(), row.authorProfile().displayName());
        author.setAvatarMediaId(row.authorProfile().avatarMediaId());
        Comment body = new Comment(
                row.comment().id(),
                row.comment().postId(),
                author,
                row.comment().visibleBody(),
                row.comment().depth(),
                OffsetDateTime.ofInstant(row.comment().createdAt(), ZoneOffset.UTC),
                row.comment().tombstoned(),
                (int) row.likeCount(),
                row.liked(),
                (int) row.replyCount());
        body.setParentId(row.comment().parentId());
        return body;
    }

    static CommentPage toPage(CommentList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new CommentPage(list.data().stream().map(CommentResponses::toApi).toList(), page);
    }
}
