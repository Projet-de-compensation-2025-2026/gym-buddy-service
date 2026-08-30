package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.auth.PasswordHasher;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.ObjectStorage;
import fr.projetcompensation.gymbuddy.posts.Post;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcFixtureGenerator implements FixtureGenerator {

    private static final Logger log = LoggerFactory.getLogger(JdbcFixtureGenerator.class);
    private static final String BULK_PASSWORD = "fixture-bulk-password";

    private final FixtureStore store;
    private final PasswordHasher passwords;
    private final ObjectStorage storage;
    private final long seed;
    private final Instant origin;
    private final String alexPassword;
    private final String blakePassword;
    private final String modPassword;
    private final String adminPassword;

    public JdbcFixtureGenerator(
            JdbcTemplate jdbc,
            PasswordHasher passwords,
            ObjectStorage storage,
            long seed,
            Instant origin,
            String alexPassword,
            String blakePassword,
            String modPassword,
            String adminPassword) {
        this.store = new FixtureStore(jdbc);
        this.passwords = passwords;
        this.storage = storage;
        this.seed = seed;
        this.origin = origin;
        this.alexPassword = alexPassword;
        this.blakePassword = blakePassword;
        this.modPassword = modPassword;
        this.adminPassword = adminPassword;
    }

    @Override
    public FixtureReport generate(FixtureMagnitude magnitude) {
        FixtureMagnitude counts = magnitude == null ? FixtureMagnitude.demo() : magnitude;
        String bulkHash = passwords.hash(BULK_PASSWORD);
        UserFactory users = new UserFactory(
                seed,
                origin,
                bulkHash,
                passwords.hash(alexPassword),
                passwords.hash(blakePassword),
                passwords.hash(modPassword),
                passwords.hash(adminPassword));
        List<UserDraft> drafts = users.create(counts.users());
        int stock = uploadStock();
        List<Media> media = new MediaFactory(seed, origin).create(drafts, counts.media());
        List<fr.projetcompensation.gymbuddy.friends.Friendship> friendships =
                new FriendshipFactory(seed, origin).create(drafts, counts.friendships());
        List<Post> posts = new PostFactory(seed, origin).create(drafts, counts.posts());
        List<fr.projetcompensation.gymbuddy.comments.Comment> comments =
                new CommentFactory(seed, origin).create(drafts, posts, counts.comments());
        EventFactory.Bundle events =
                new EventFactory(seed, origin).create(drafts, counts.events(), counts.applications());
        MessageFactory.Bundle messages = new MessageFactory(seed, origin).create(friendships, counts.messages());

        store.insertUsers(drafts.stream().map(UserDraft::user).toList());
        store.insertProfiles(drafts.stream().map(UserDraft::profile).toList());
        store.insertMedia(media);
        store.insertFriendships(friendships);
        store.insertPosts(posts);
        store.insertPostMedia(posts, media);
        store.insertComments(
                comments.stream().filter(comment -> comment.parentId() == null).toList());
        store.insertComments(
                comments.stream().filter(comment -> comment.parentId() != null).toList());
        store.insertEvents(events.events());
        store.insertOccurrences(events.occurrences());
        store.insertApplications(events.applications());
        store.insertConversations(messages.conversations());
        store.insertMessages(messages.messages());

        FixtureReport report = new FixtureReport(
                drafts.size(),
                friendships.size(),
                posts.size(),
                comments.size(),
                events.events().size(),
                events.applications().size(),
                messages.messages().size(),
                media.size(),
                stock);
        log.info(
                "fixtures generated users={} friendships={} posts={} comments={} events={} applications={} messages={} media={} stock={}",
                report.users(),
                report.friendships(),
                report.posts(),
                report.comments(),
                report.events(),
                report.applications(),
                report.messages(),
                report.media(),
                report.stockObjects());
        return report;
    }

    @Override
    public void reset(UUID preserveUserId) {
        store.reset(preserveUserId);
        log.info("fixtures truncated preserve={}", preserveUserId);
    }

    private int uploadStock() {
        if (storage == null) {
            return 0;
        }
        int uploaded = 0;
        byte[] jpeg = StockImages.jpeg();
        for (int i = 0; i < StockImages.COUNT; i++) {
            String key = StockImages.key(i);
            try {
                storage.put(key, StockImages.MIME, jpeg);
                uploaded++;
            } catch (RuntimeException ex) {
                log.warn("stock object {} skipped: {}", key, ex.getMessage());
            }
        }
        return uploaded;
    }
}
