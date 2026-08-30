package fr.projetcompensation.gymbuddy.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDraft(
        String title,
        String description,
        String activity,
        String place,
        Double lat,
        Double lng,
        Instant startsAt,
        Integer durationMin,
        String visibility,
        Integer capacity,
        String recurrence,
        List<String> tags,
        UUID coverMediaId,
        List<UUID> inviteeIds) {}
