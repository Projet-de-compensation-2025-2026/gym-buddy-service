package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.events.EventService;
import fr.projetcompensation.gymbuddy.matching.MatchingService;
import fr.projetcompensation.gymbuddy.matching.MatchingStore;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionGraph;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionService;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionStore;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionWeights;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SuggestionConfiguration {

    @Bean
    SuggestionWeights suggestionWeights() {
        return SuggestionWeights.defaults();
    }

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    SuggestionService suggestionService(
            SuggestionGraph graph, SuggestionStore store, SuggestionWeights weights, Clock clock) {
        return new SuggestionService(graph, store, weights, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    MatchingService matchingService(
            MatchingStore store, SuggestionGraph graph, Clock clock, ObjectProvider<EventService> events) {
        return new MatchingService(store, graph, clock, events.getIfAvailable());
    }
}
