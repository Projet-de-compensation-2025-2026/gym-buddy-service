package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.comments.Comment;
import fr.projetcompensation.gymbuddy.posts.Post;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;

public final class CommentFactory {

    private final Faker faker;
    private final Random random;
    private final long seed;
    private final Instant origin;

    public CommentFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
        this.random = new Random(seed + 31);
        this.faker = new Faker(java.util.Locale.ENGLISH, random);
    }

    public List<Comment> create(List<UserDraft> users, List<Post> posts, int count) {
        if (users.isEmpty() || posts.isEmpty() || count <= 0) {
            return List.of();
        }
        List<Comment> rows = new ArrayList<>(count);
        List<UUID> roots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Post post = posts.get(i % posts.size());
            UserDraft author = users.get((i * 3 + 1) % users.size());
            boolean reply = i > 0 && i % 5 == 0 && !roots.isEmpty();
            UUID parent = reply ? roots.get(random.nextInt(roots.size())) : null;
            int depth = parent == null ? 0 : 1;
            String body = faker.lorem().sentence(6);
            if (body.length() > 1000) {
                body = body.substring(0, 1000);
            }
            UUID id = FixtureIds.of(seed, "comment", i);
            if (parent == null) {
                roots.add(id);
            }
            rows.add(new Comment(
                    id, post.id(), author.id(), parent, body, depth, origin.plusSeconds(7_200L + i * 11L), null, null));
        }
        return List.copyOf(rows);
    }
}
