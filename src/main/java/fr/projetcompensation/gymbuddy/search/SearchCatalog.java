package fr.projetcompensation.gymbuddy.search;

import java.util.List;

public interface SearchCatalog {

    List<PersonCandidate> people();

    List<EventCandidate> events();
}
