package fr.projetcompensation.gymbuddy.search;

import java.util.List;

public record EventSearchList(List<EventSearchHit> data, String next, int size) {}
