package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.matching.MatchingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "DATABASE_URL")
public class SuggestionJobs {

    private final SuggestionService suggestions;
    private final MatchingService matching;

    public SuggestionJobs(SuggestionService suggestions, MatchingService matching) {
        this.suggestions = suggestions;
        this.matching = matching;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    public void nightly() {
        suggestions.recomputeAll();
        matching.assignCurrentWeek();
    }
}
