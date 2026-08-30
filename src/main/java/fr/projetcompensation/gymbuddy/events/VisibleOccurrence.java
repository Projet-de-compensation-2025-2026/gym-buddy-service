package fr.projetcompensation.gymbuddy.events;

public record VisibleOccurrence(EventOccurrence occurrence, int acceptedCount, int remainingSeats) {}
