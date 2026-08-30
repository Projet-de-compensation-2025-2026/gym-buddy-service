package fr.projetcompensation.gymbuddy.events.http;

import fr.projetcompensation.gymbuddy.events.EventList;
import fr.projetcompensation.gymbuddy.events.VisibleApplicant;
import fr.projetcompensation.gymbuddy.events.VisibleApplication;
import fr.projetcompensation.gymbuddy.events.VisibleEvent;
import fr.projetcompensation.gymbuddy.events.VisibleOccurrence;
import fr.projetcompensation.gymbuddy.openapi.model.Event;
import fr.projetcompensation.gymbuddy.openapi.model.EventApplicant;
import fr.projetcompensation.gymbuddy.openapi.model.EventApplication;
import fr.projetcompensation.gymbuddy.openapi.model.EventOccurrence;
import fr.projetcompensation.gymbuddy.openapi.model.EventPage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.openapi.model.PostAuthor;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class EventResponses {

    private EventResponses() {}

    public static Event toApi(VisibleEvent row) {
        Event body = new Event(
                row.event().id(),
                author(row.organizer(), row.organizerProfile()),
                row.event().title(),
                row.event().activity(),
                row.event().place(),
                OffsetDateTime.ofInstant(row.event().startsAt(), ZoneOffset.UTC),
                row.event().durationMin(),
                Event.VisibilityEnum.fromValue(row.event().visibility().wireValue()),
                row.event().capacity(),
                row.remainingSeats(),
                Event.KindEnum.fromValue(row.event().kindWire()),
                row.event().tags(),
                OffsetDateTime.ofInstant(row.event().createdAt(), ZoneOffset.UTC),
                occurrences(row.occurrences()),
                pending(row.pendingApplicants()));
        body.setDescription(row.event().description());
        body.setLat(row.event().lat());
        body.setLng(row.event().lng());
        body.setRecurrence(row.event().recurrence());
        body.setCoverMediaId(row.event().coverMediaId());
        if (row.event().cancelledAt() != null) {
            body.setCancelledAt(OffsetDateTime.ofInstant(row.event().cancelledAt(), ZoneOffset.UTC));
        }
        body.setUpdatedAfterAccept(row.event().updatedAfterAccept());
        if (row.viewerApplication() != null) {
            body.setViewerApplication(toApplication(row.viewerApplication()));
        }
        if (!row.inviteeIds().isEmpty()) {
            body.setInviteeIds(row.inviteeIds());
        }
        return body;
    }

    public static EventPage toPage(EventList list) {
        Page page = new Page(list.size());
        page.setNext(list.next());
        return new EventPage(list.data().stream().map(EventResponses::toApi).toList(), page);
    }

    public static EventApplication toApi(VisibleApplication row) {
        return toApplication(row);
    }

    public static EventApplication toApplication(VisibleApplication row) {
        EventApplication body = new EventApplication(
                row.application().id(),
                row.application().eventId(),
                row.application().occurrenceId(),
                author(row.applicant(), row.profile()),
                EventApplication.StatusEnum.fromValue(row.application().status().wireValue()),
                OffsetDateTime.ofInstant(row.application().createdAt(), ZoneOffset.UTC));
        if (row.application().respondedAt() != null) {
            body.setRespondedAt(OffsetDateTime.ofInstant(row.application().respondedAt(), ZoneOffset.UTC));
        }
        return body;
    }

    private static List<EventOccurrence> occurrences(List<VisibleOccurrence> rows) {
        List<EventOccurrence> data = new ArrayList<>();
        for (VisibleOccurrence row : rows) {
            data.add(new EventOccurrence(
                    row.occurrence().id(),
                    row.occurrence().eventId(),
                    OffsetDateTime.ofInstant(row.occurrence().startsAt(), ZoneOffset.UTC),
                    row.remainingSeats(),
                    row.acceptedCount(),
                    row.occurrence().cancelled()));
        }
        return data;
    }

    private static List<EventApplicant> pending(List<VisibleApplicant> rows) {
        List<EventApplicant> data = new ArrayList<>();
        for (VisibleApplicant row : rows) {
            data.add(new EventApplicant(
                    toApplication(new VisibleApplication(row.application(), row.applicant(), row.profile())),
                    row.matchingScore()));
        }
        return data;
    }

    private static PostAuthor author(User user, Profile profile) {
        PostAuthor author = new PostAuthor(user.id(), user.handle(), profile.displayName());
        author.setAvatarMediaId(profile.avatarMediaId());
        return author;
    }
}
