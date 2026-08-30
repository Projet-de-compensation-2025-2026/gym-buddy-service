package fr.projetcompensation.gymbuddy.events;

import java.util.List;

public record EventList(List<VisibleEvent> data, String next, int size) {}
