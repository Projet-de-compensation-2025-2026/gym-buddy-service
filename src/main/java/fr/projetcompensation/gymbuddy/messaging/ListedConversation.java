package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.Instant;

public record ListedConversation(
        Conversation conversation,
        User peer,
        Profile peerProfile,
        Message lastMessage,
        long unreadCount,
        Instant updatedAt) {}
