package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.comments.Comment;
import fr.projetcompensation.gymbuddy.events.Event;
import fr.projetcompensation.gymbuddy.events.EventApplication;
import fr.projetcompensation.gymbuddy.events.EventOccurrence;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.messaging.Conversation;
import fr.projetcompensation.gymbuddy.messaging.Message;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class FixtureStore {

    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;

    FixtureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset(UUID preserveUserId) {
        jdbc.update("DELETE FROM audit_events");
        if (preserveUserId == null) {
            jdbc.update("DELETE FROM users");
            return;
        }
        jdbc.update("UPDATE profiles SET avatar_media_id = NULL WHERE user_id = ?", preserveUserId);
        jdbc.update("DELETE FROM users WHERE id <> ?", preserveUserId);
    }

    void insertUsers(List<User> users) {
        batch(users, """
                INSERT INTO users (id, email, handle, password_hash, role, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, user) -> {
            ps.setObject(1, user.id());
            ps.setString(2, user.email());
            ps.setString(3, user.handle());
            ps.setString(4, user.passwordHash());
            ps.setString(5, user.role().wireValue());
            ps.setString(6, user.status().wireValue());
            ps.setTimestamp(7, Timestamp.from(user.createdAt()));
        });
    }

    void insertProfiles(List<Profile> profiles) {
        batch(profiles, """
                INSERT INTO profiles (user_id, display_name, bio, visibility, sports, experience_level,
                    city, lat, lng, preferred_windows, avatar_media_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (user_id) DO NOTHING
                """, (ps, profile) -> {
            ps.setObject(1, profile.userId());
            ps.setString(2, profile.displayName());
            ps.setString(3, profile.bio());
            ps.setString(4, profile.visibility().wireValue());
            ps.setArray(
                    5, ps.getConnection().createArrayOf("text", profile.sports().toArray(String[]::new)));
            if (profile.experienceLevel() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, profile.experienceLevel().wireValue());
            }
            ps.setString(7, profile.city());
            setNullableDouble(ps, 8, profile.lat());
            setNullableDouble(ps, 9, profile.lng());
            ps.setString(10, windowsJson(profile.preferredWindows()));
            ps.setObject(11, profile.avatarMediaId());
        });
    }

    void insertMedia(List<Media> media) {
        batch(media, """
                INSERT INTO media (id, owner_id, kind, mime, bytes, variant_bytes, status, object_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.ownerId());
            ps.setString(3, row.kind().wireValue());
            ps.setString(4, row.mime());
            ps.setLong(5, row.bytes());
            ps.setLong(6, row.variantBytes());
            ps.setString(7, row.status().wireValue());
            ps.setString(8, row.objectKey());
            ps.setTimestamp(9, Timestamp.from(row.createdAt()));
        });
    }

    void insertFriendships(List<Friendship> friendships) {
        batch(friendships, """
                INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.requesterId());
            ps.setObject(3, row.addresseeId());
            ps.setString(4, row.status().wireValue());
            ps.setTimestamp(5, Timestamp.from(row.createdAt()));
            ps.setTimestamp(6, Timestamp.from(row.respondedAt()));
        });
    }

    void insertPosts(List<Post> posts) {
        batch(posts, """
                INSERT INTO posts (id, author_id, body, visibility, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.authorId());
            ps.setString(3, row.body());
            ps.setString(4, row.visibility().wireValue());
            ps.setTimestamp(5, Timestamp.from(row.createdAt()));
        });
    }

    void insertPostMedia(List<Post> posts, List<Media> media) {
        int n = Math.min(posts.size(), media.size());
        if (n <= 0) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO post_media (post_id, media_id, position)
                VALUES (?, ?, 0)
                ON CONFLICT (post_id, position) DO NOTHING
                """, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setObject(1, posts.get(i).id());
                ps.setObject(2, media.get(i).id());
            }

            @Override
            public int getBatchSize() {
                return n;
            }
        });
    }

    void insertComments(List<Comment> comments) {
        batch(comments, """
                INSERT INTO comments (id, post_id, author_id, parent_id, body, depth, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.postId());
            ps.setObject(3, row.authorId());
            ps.setObject(4, row.parentId());
            ps.setString(5, row.body());
            ps.setInt(6, row.depth());
            ps.setTimestamp(7, Timestamp.from(row.createdAt()));
        });
    }

    void insertEvents(List<Event> events) {
        batch(events, """
                INSERT INTO events (id, organizer_id, title, description, activity, place, lat, lng,
                    starts_at, duration_min, visibility, capacity, recurrence, tags, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.organizerId());
            ps.setString(3, row.title());
            ps.setString(4, row.description());
            ps.setString(5, row.activity());
            ps.setString(6, row.place());
            setNullableDouble(ps, 7, row.lat());
            setNullableDouble(ps, 8, row.lng());
            ps.setTimestamp(9, Timestamp.from(row.startsAt()));
            ps.setInt(10, row.durationMin());
            ps.setString(11, row.visibility().wireValue());
            ps.setInt(12, row.capacity());
            ps.setString(13, row.recurrence());
            ps.setArray(14, ps.getConnection().createArrayOf("text", row.tags().toArray(String[]::new)));
            ps.setTimestamp(15, Timestamp.from(row.createdAt()));
        });
    }

    void insertOccurrences(List<EventOccurrence> occurrences) {
        batch(occurrences, """
                INSERT INTO event_occurrences (id, event_id, starts_at)
                VALUES (?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.eventId());
            ps.setTimestamp(3, Timestamp.from(row.startsAt()));
        });
    }

    void insertApplications(List<EventApplication> applications) {
        batch(applications, """
                INSERT INTO event_applications (id, event_id, occurrence_id, user_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.eventId());
            ps.setObject(3, row.occurrenceId());
            ps.setObject(4, row.applicantId());
            ps.setString(5, row.status().wireValue());
            ps.setTimestamp(6, Timestamp.from(row.createdAt()));
            if (row.respondedAt() == null) {
                ps.setTimestamp(7, null);
            } else {
                ps.setTimestamp(7, Timestamp.from(row.respondedAt()));
            }
        });
    }

    void insertConversations(List<Conversation> conversations) {
        batch(conversations, """
                INSERT INTO conversations (id, user_lo, user_hi, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.userLo());
            ps.setObject(3, row.userHi());
            ps.setTimestamp(4, Timestamp.from(row.createdAt()));
        });
    }

    void insertMessages(List<Message> messages) {
        batch(messages, """
                INSERT INTO messages (id, conversation_id, sender_id, type, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.conversationId());
            ps.setObject(3, row.senderId());
            ps.setString(4, row.type().wireValue());
            ps.setString(5, row.body());
            ps.setTimestamp(6, Timestamp.from(row.createdAt()));
        });
    }

    private <T> void batch(List<T> rows, String sql, Binder<T> binder) {
        if (rows.isEmpty()) {
            return;
        }
        for (int start = 0; start < rows.size(); start += BATCH) {
            int end = Math.min(start + BATCH, rows.size());
            List<T> slice = rows.subList(start, end);
            jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    binder.bind(ps, slice.get(i));
                }

                @Override
                public int getBatchSize() {
                    return slice.size();
                }
            });
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static String windowsJson(List<PreferredWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            PreferredWindow window = windows.get(i);
            json.append("{\"weekday\":")
                    .append(window.weekday())
                    .append(",\"start\":\"")
                    .append(window.start())
                    .append("\",\"end\":\"")
                    .append(window.end())
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }
}
