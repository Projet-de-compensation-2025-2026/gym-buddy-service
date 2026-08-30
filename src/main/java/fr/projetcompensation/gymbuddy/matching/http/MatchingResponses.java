package fr.projetcompensation.gymbuddy.matching.http;

import fr.projetcompensation.gymbuddy.matching.MatchingState;
import fr.projetcompensation.gymbuddy.matching.ProposedMatch;
import fr.projetcompensation.gymbuddy.openapi.model.MatchingDraftEvent;
import fr.projetcompensation.gymbuddy.openapi.model.MatchingMe;
import fr.projetcompensation.gymbuddy.openapi.model.MatchingPeer;
import fr.projetcompensation.gymbuddy.suggestions.MemberSnapshot;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class MatchingResponses {

    private MatchingResponses() {}

    static MatchingMe toApi(MatchingState state) {
        MatchingMe body = new MatchingMe(state.optedIn(), state.weekStart());
        if (state.pair() != null) {
            body.setPair(peer(state.pair()));
        }
        if (state.match() != null) {
            body.setEvent(event(state.match()));
        }
        return body;
    }

    private static MatchingPeer peer(MemberSnapshot member) {
        MatchingPeer peer = new MatchingPeer(member.userId(), member.handle(), member.displayName());
        peer.setSports(member.sports());
        peer.setCity(member.city());
        peer.setAvatarMediaId(member.avatarMediaId());
        return peer;
    }

    private static MatchingDraftEvent event(ProposedMatch match) {
        MatchingDraftEvent event = new MatchingDraftEvent(
                match.activity(),
                OffsetDateTime.ofInstant(match.startsAt(), ZoneOffset.UTC),
                match.durationMin(),
                1,
                MatchingDraftEvent.VisibilityEnum.FRIENDS);
        event.setId(match.eventId());
        return event;
    }
}
