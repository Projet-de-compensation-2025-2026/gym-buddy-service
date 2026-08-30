package fr.projetcompensation.gymbuddy.search.http;

import fr.projetcompensation.gymbuddy.openapi.model.EventSearchHit;
import fr.projetcompensation.gymbuddy.openapi.model.EventSearchPage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.PeopleSearchHit;
import fr.projetcompensation.gymbuddy.openapi.model.PeopleSearchPage;
import fr.projetcompensation.gymbuddy.openapi.model.PostAuthor;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.search.EventSearchList;
import fr.projetcompensation.gymbuddy.search.PeopleSearchList;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class SearchResponses {

    private SearchResponses() {}

    static PeopleSearchPage toPeoplePage(PeopleSearchList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new PeopleSearchPage(list.data().stream().map(SearchResponses::toApi).toList(), page);
    }

    static EventSearchPage toEventPage(EventSearchList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new EventSearchPage(list.data().stream().map(SearchResponses::toApi).toList(), page);
    }

    static PeopleSearchHit toApi(fr.projetcompensation.gymbuddy.search.PeopleSearchHit hit) {
        Profile profile = hit.profile();
        User user = hit.user();
        PeopleSearchHit body = new PeopleSearchHit(
                user.handle(),
                profile.displayName(),
                PeopleSearchHit.VisibilityEnum.fromValue(profile.visibility().wireValue()),
                profile.sports(),
                PeopleSearchHit.FriendStateEnum.fromValue(hit.friendState().wireValue()));
        body.setAvatarMediaId(profile.avatarMediaId());
        body.setCity(profile.city());
        body.setExperienceLevel(experience(profile.experienceLevel()));
        body.setDistanceKm(hit.distanceKm());
        body.setMatchReason(hit.matchReason());
        return body;
    }

    static EventSearchHit toApi(fr.projetcompensation.gymbuddy.search.EventSearchHit hit) {
        PostAuthor organizer = new PostAuthor(
                hit.organizer().id(), hit.organizer().handle(), hit.organizerProfile().displayName());
        organizer.setAvatarMediaId(hit.organizerProfile().avatarMediaId());
        EventSearchHit body = new EventSearchHit(
                hit.id(),
                hit.title(),
                hit.activity(),
                hit.place(),
                OffsetDateTime.ofInstant(hit.startsAt() == null ? Instant.EPOCH : hit.startsAt(), ZoneOffset.UTC),
                hit.remainingSeats(),
                hit.capacity(),
                organizer);
        body.setDistanceKm(hit.distanceKm());
        body.setMatchReason(hit.matchReason());
        return body;
    }

    private static PeopleSearchHit.ExperienceLevelEnum experience(ExperienceLevel level) {
        if (level == null) {
            return null;
        }
        return PeopleSearchHit.ExperienceLevelEnum.fromValue(level.wireValue());
    }
}
