package fr.projetcompensation.gymbuddy.messaging;

import java.util.List;

public record MessageList(List<Message> data, String next, int size) {}
