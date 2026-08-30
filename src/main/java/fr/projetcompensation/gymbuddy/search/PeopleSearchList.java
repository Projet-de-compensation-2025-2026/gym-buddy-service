package fr.projetcompensation.gymbuddy.search;

import java.util.List;

public record PeopleSearchList(List<PeopleSearchHit> data, String next, int size) {}
