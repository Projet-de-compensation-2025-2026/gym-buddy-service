package fr.projetcompensation.gymbuddy.fixtures;

/**
 * Default order of magnitude from 07-Test-fixtures.md. Integration tests use
 * {@link #tiny()} (tens of rows), never the 3 000-user demo set.
 */
public record FixtureMagnitude(
        int users, int friendships, int posts, int comments, int events, int applications, int messages, int media) {

    public static final FixtureMagnitude DEMO = new FixtureMagnitude(3000, 12000, 15000, 20000, 800, 4000, 10000, 5000);

    public static FixtureMagnitude tiny() {
        return new FixtureMagnitude(12, 20, 15, 20, 5, 8, 10, 12);
    }

    public static FixtureMagnitude demo() {
        return DEMO;
    }

    public FixtureMagnitude {
        users = clamp(users, 0, 10_000);
        friendships = clamp(friendships, 0, 50_000);
        posts = clamp(posts, 0, 50_000);
        comments = clamp(comments, 0, 80_000);
        events = clamp(events, 0, 5_000);
        applications = clamp(applications, 0, 20_000);
        messages = clamp(messages, 0, 50_000);
        media = clamp(media, 0, 20_000);
    }

    public static FixtureMagnitude of(
            Integer users,
            Integer friendships,
            Integer posts,
            Integer comments,
            Integer events,
            Integer applications,
            Integer messages,
            Integer media) {
        FixtureMagnitude fallback = DEMO;
        return new FixtureMagnitude(
                users == null ? fallback.users : users,
                friendships == null ? fallback.friendships : friendships,
                posts == null ? fallback.posts : posts,
                comments == null ? fallback.comments : comments,
                events == null ? fallback.events : events,
                applications == null ? fallback.applications : applications,
                messages == null ? fallback.messages : messages,
                media == null ? fallback.media : media);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
