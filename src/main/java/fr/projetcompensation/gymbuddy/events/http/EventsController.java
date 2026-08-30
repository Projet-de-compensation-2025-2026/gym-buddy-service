package fr.projetcompensation.gymbuddy.events.http;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.events.EventDraft;
import fr.projetcompensation.gymbuddy.events.EventService;
import fr.projetcompensation.gymbuddy.openapi.api.EventsApi;
import fr.projetcompensation.gymbuddy.openapi.model.CancelEventRequest;
import fr.projetcompensation.gymbuddy.openapi.model.CreateEventApplicationRequest;
import fr.projetcompensation.gymbuddy.openapi.model.CreateEventRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Event;
import fr.projetcompensation.gymbuddy.openapi.model.EventApplication;
import fr.projetcompensation.gymbuddy.openapi.model.EventPage;
import fr.projetcompensation.gymbuddy.openapi.model.PatchEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventsController implements EventsApi {

    private final ObjectProvider<EventService> events;
    private final HttpServletRequest httpRequest;

    public EventsController(ObjectProvider<EventService> events, HttpServletRequest httpRequest) {
        this.events = events;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<EventPage> getEvents(
            @Nullable String kind,
            @Nullable OffsetDateTime from,
            @Nullable OffsetDateTime until,
            @Nullable String after,
            Integer size) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(EventResponses.toPage(
                service().list(principal.userId(), kind, instant(from), instant(until), after, size)));
    }

    @Override
    public ResponseEntity<Event> postEvents(CreateEventRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EventResponses.toApi(service().create(principal.userId(), toDraft(request))));
    }

    @Override
    public ResponseEntity<Event> getEventsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(EventResponses.toApi(service().get(principal.userId(), id)));
    }

    @Override
    public ResponseEntity<Event> patchEventsId(UUID id, PatchEventRequest request) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(EventResponses.toApi(service().patch(principal.userId(), id, toDraft(request))));
    }

    @Override
    public ResponseEntity<Event> postEventsIdCancel(UUID id, @Nullable CancelEventRequest request) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        UUID occurrenceId = request == null ? null : request.getOccurrenceId();
        return ResponseEntity.ok(EventResponses.toApi(service().cancel(principal.userId(), id, occurrenceId)));
    }

    @Override
    public ResponseEntity<EventApplication> postEventsIdApplications(
            UUID id, @Nullable String idempotencyKey, @Nullable CreateEventApplicationRequest request) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        UUID occurrenceId = request == null ? null : request.getOccurrenceId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EventResponses.toApi(service().apply(principal.userId(), id, occurrenceId)));
    }

    @Override
    public ResponseEntity<Void> deleteApplicationsId(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().withdraw(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<EventApplication> postApplicationsIdAccept(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        return ResponseEntity.ok(EventResponses.toApi(service().accept(principal.userId(), id)));
    }

    @Override
    public ResponseEntity<Void> postApplicationsIdDecline(UUID id) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        service().decline(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private EventService service() {
        EventService service = events.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("events are not configured");
        }
        return service;
    }

    private static EventDraft toDraft(CreateEventRequest request) {
        String visibility =
                request.getVisibility() == null ? null : request.getVisibility().getValue();
        List<UUID> invitees = request.getInviteeIds() == null ? List.of() : List.copyOf(request.getInviteeIds());
        List<String> tags = request.getTags() == null ? List.of() : List.copyOf(request.getTags());
        return new EventDraft(
                request.getTitle(),
                request.getDescription(),
                request.getActivity(),
                request.getPlace(),
                request.getLat(),
                request.getLng(),
                instant(request.getStartsAt()),
                request.getDurationMin(),
                visibility,
                request.getCapacity(),
                request.getRecurrence(),
                tags,
                request.getCoverMediaId(),
                invitees);
    }

    private static EventDraft toDraft(PatchEventRequest request) {
        List<UUID> invitees = request.getInviteeIds();
        List<String> tags = request.getTags();
        return new EventDraft(
                request.getTitle(),
                request.getDescription(),
                request.getActivity(),
                request.getPlace(),
                request.getLat(),
                request.getLng(),
                instant(request.getStartsAt()),
                request.getDurationMin(),
                null,
                null,
                null,
                tags,
                request.getCoverMediaId(),
                invitees);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
