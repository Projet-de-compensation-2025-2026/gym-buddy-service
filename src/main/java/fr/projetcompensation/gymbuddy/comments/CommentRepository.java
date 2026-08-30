package fr.projetcompensation.gymbuddy.comments;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    void save(Comment comment);

    void update(Comment comment);

    Optional<Comment> findById(UUID id);

    List<Comment> listRoots(UUID postId, InstantIdCursor before, int limit);

    List<Comment> listReplies(UUID parentId, InstantIdCursor before, int limit);

    long replyCount(UUID parentId);

    boolean insertLike(UUID userId, UUID commentId, Instant at);

    boolean deleteLike(UUID userId, UUID commentId);

    boolean liked(UUID userId, UUID commentId);

    long likeCount(UUID commentId);
}
