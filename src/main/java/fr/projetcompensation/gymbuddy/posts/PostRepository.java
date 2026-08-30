package fr.projetcompensation.gymbuddy.posts;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    void save(Post post, List<UUID> mediaIds);

    void update(Post post);

    Optional<Post> findById(UUID id);

    Optional<Post> findByMediaId(UUID mediaId);

    List<UUID> mediaIds(UUID postId);

    boolean insertLike(UUID userId, UUID postId, Instant at);

    boolean deleteLike(UUID userId, UUID postId);

    boolean liked(UUID userId, UUID postId);

    long likeCount(UUID postId);

    List<LikeRow> listLikes(UUID postId, InstantIdCursor after, int limit);

    boolean insertRepost(UUID userId, UUID postId, Instant at);

    boolean deleteRepost(UUID userId, UUID postId);

    boolean reposted(UUID userId, UUID postId);

    long repostCount(UUID postId);
}
