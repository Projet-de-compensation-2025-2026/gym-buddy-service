package fr.projetcompensation.gymbuddy.suggestions.http;

import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.Suggestion;
import fr.projetcompensation.gymbuddy.openapi.model.SuggestionPage;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionList;
import fr.projetcompensation.gymbuddy.suggestions.VisibleSuggestion;

final class SuggestionResponses {

    private SuggestionResponses() {}

    static SuggestionPage toPage(SuggestionList list) {
        Page page = new Page(list.size());
        page.setNext(null);
        return new SuggestionPage(
                list.data().stream().map(SuggestionResponses::toApi).toList(), page);
    }

    static Suggestion toApi(VisibleSuggestion row) {
        Suggestion body = new Suggestion(
                row.candidate().userId(),
                row.candidate().handle(),
                row.displayName(),
                row.stub() ? Suggestion.ViewEnum.STUB : Suggestion.ViewEnum.FULL,
                row.scored().sharedSports(),
                row.scored().mutualFriends(),
                row.scored().reason());
        body.setCity(row.city());
        body.setAvatarMediaId(row.candidate().avatarMediaId());
        return body;
    }
}
