-- #64: events, occurrences, invites, and applications (FS-EVT-01..13).
CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    activity TEXT NOT NULL,
    place TEXT NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    starts_at TIMESTAMPTZ NOT NULL,
    duration_min INTEGER NOT NULL,
    visibility TEXT NOT NULL,
    capacity INTEGER NOT NULL,
    recurrence TEXT,
    tags TEXT[] NOT NULL DEFAULT '{}',
    cover_media_id UUID REFERENCES media (id),
    cancelled_at TIMESTAMPTZ,
    updated_after_accept BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    hidden_at TIMESTAMPTZ,
    CONSTRAINT events_visibility_check CHECK (visibility IN ('public', 'friends', 'private')),
    CONSTRAINT events_capacity_check CHECK (capacity BETWEEN 1 AND 100),
    CONSTRAINT events_duration_check CHECK (duration_min BETWEEN 1 AND 1440),
    CONSTRAINT events_title_len CHECK (char_length(title) BETWEEN 1 AND 120),
    CONSTRAINT events_activity_len CHECK (char_length(activity) BETWEEN 2 AND 32),
    CONSTRAINT events_place_len CHECK (char_length(place) BETWEEN 1 AND 200)
);

CREATE INDEX events_organizer_starts ON events (organizer_id, starts_at, id);
CREATE INDEX events_starts ON events (starts_at, id);
CREATE UNIQUE INDEX events_cover_media_id_unique ON events (cover_media_id) WHERE cover_media_id IS NOT NULL;

CREATE TABLE event_occurrences (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT event_occurrences_unique_start UNIQUE (event_id, starts_at)
);

CREATE INDEX event_occurrences_starts ON event_occurrences (starts_at, id);
CREATE INDEX event_occurrences_event ON event_occurrences (event_id, starts_at, id);

CREATE TABLE event_invites (
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, user_id)
);

CREATE TABLE event_applications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    occurrence_id UUID NOT NULL REFERENCES event_occurrences (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT event_applications_status_check
        CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled', 'withdrawn')),
    CONSTRAINT event_applications_unique_applicant UNIQUE (occurrence_id, user_id)
);

CREATE INDEX event_applications_occurrence_status ON event_applications (occurrence_id, status);
CREATE INDEX event_applications_event_user ON event_applications (event_id, user_id);
