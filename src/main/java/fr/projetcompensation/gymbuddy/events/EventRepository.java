package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    void save(Event event, List<EventOccurrence> occurrences, List<UUID> inviteeIds);

    void update(Event event);

    void replaceInvitees(UUID eventId, List<UUID> inviteeIds);

    void saveOccurrences(List<EventOccurrence> occurrences);

    void updateOccurrence(EventOccurrence occurrence);

    Optional<Event> findById(UUID id);

    Optional<Event> findByCoverMediaId(UUID mediaId);

    List<EventOccurrence> occurrences(UUID eventId);

    Optional<EventOccurrence> findOccurrence(UUID id);

    Optional<EventOccurrence> lockOccurrence(UUID id);

    List<UUID> inviteeIds(UUID eventId);

    boolean isInvitee(UUID eventId, UUID userId);

    void saveApplication(EventApplication application);

    void updateApplication(EventApplication application);

    Optional<EventApplication> findApplication(UUID id);

    Optional<EventApplication> findApplication(UUID occurrenceId, UUID applicantId);

    List<EventApplication> applicationsForEvent(UUID eventId);

    List<EventApplication> pendingForOccurrence(UUID occurrenceId);

    boolean hasAccepted(UUID eventId, UUID userId);

    int countAccepted(UUID occurrenceId);

    int countAcceptedCoAttendance(UUID organizerId, UUID applicantId);

    List<Event> listVisible(
            UUID viewerId, String kind, Instant from, Instant until, InstantIdCursor after, int limit);
}
