package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostVisibility;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.datafaker.Faker;

public final class PostFactory {

    private final Faker faker;
    private final Random random;
    private final long seed;
    private final Instant origin;

    public PostFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
        this.random = new Random(seed + 17);
        this.faker = new Faker(Locale.ENGLISH, random);
    }

    public List<Post> create(List<UserDraft> users, int count) {
        if (users.isEmpty() || count <= 0) {
            return List.of();
        }
        List<Post> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UserDraft author = users.get(i % users.size());
            PostVisibility visibility = i % 5 == 0 ? PostVisibility.PUBLIC : PostVisibility.FRIENDS;
            String body = faker.lorem().sentence(8) + " #" + author.sport();
            if (body.length() > 2000) {
                body = body.substring(0, 2000);
            }
            rows.add(new Post(
                    FixtureIds.of(seed, "post", i),
                    author.id(),
                    body,
                    visibility,
                    origin.plusSeconds(3_600L + i * 17L),
                    null,
                    null,
                    null,
                    null));
        }
        return List.copyOf(rows);
    }
}
