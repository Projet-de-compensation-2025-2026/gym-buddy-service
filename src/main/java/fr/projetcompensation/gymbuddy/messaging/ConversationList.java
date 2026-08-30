package fr.projetcompensation.gymbuddy.messaging;

import java.util.List;

public record ConversationList(List<ListedConversation> data, String next, int size) {}
