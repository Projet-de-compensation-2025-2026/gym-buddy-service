package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.messaging.Conversation;
import fr.projetcompensation.gymbuddy.messaging.Message;
import fr.projetcompensation.gymbuddy.messaging.MessageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;

public final class MessageFactory {

    public record Bundle(List<Conversation> conversations, List<Message> messages) {}

    private final Faker faker;
    private final Random random;
    private final long seed;
    private final Instant origin;

    public MessageFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
        this.random = new Random(seed + 61);
        this.faker = new Faker(java.util.Locale.ENGLISH, random);
    }

    public Bundle create(List<Friendship> friendships, int messageCount) {
        if (friendships.isEmpty() || messageCount <= 0) {
            return new Bundle(List.of(), List.of());
        }
        int conversationCount = Math.max(1, Math.min(friendships.size(), Math.max(1, messageCount / 4)));
        List<Conversation> conversations = new ArrayList<>(conversationCount);
        for (int i = 0; i < conversationCount; i++) {
            Friendship pair = friendships.get(i);
            conversations.add(new Conversation(
                    FixtureIds.of(seed, "conversation", i),
                    lo(pair.requesterId(), pair.addresseeId()),
                    hi(pair.requesterId(), pair.addresseeId()),
                    origin.plusSeconds(i)));
        }
        List<Message> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            Conversation conversation = conversations.get(i % conversations.size());
            boolean fromLo = i % 2 == 0;
            String body = faker.lorem().sentence(7);
            if (body.length() > 4000) {
                body = body.substring(0, 4000);
            }
            messages.add(new Message(
                    FixtureIds.of(seed, "message", i),
                    conversation.id(),
                    fromLo ? conversation.userLo() : conversation.userHi(),
                    MessageType.TEXT,
                    body,
                    null,
                    origin.plusSeconds(12_000L + i * 13L),
                    null));
        }
        return new Bundle(List.copyOf(conversations), List.copyOf(messages));
    }

    /** PostgreSQL uuid `<` is unsigned; Java {@code UUID.compareTo} is signed. */
    static UUID lo(UUID left, UUID right) {
        return unsignedLess(left, right) ? left : right;
    }

    static UUID hi(UUID left, UUID right) {
        return unsignedLess(left, right) ? right : left;
    }

    private static boolean unsignedLess(UUID left, UUID right) {
        int cmp = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        if (cmp == 0) {
            cmp = Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
        }
        return cmp < 0;
    }
}
