-- #69: reports queue, append-only staff audit, and hide columns (FS-ADM-06, FS-ADM-07).
CREATE TABLE reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id UUID NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reports_target_type_check CHECK (target_type IN ('user', 'post', 'comment', 'event')),
    CONSTRAINT reports_status_check CHECK (status IN ('open', 'resolved')),
    CONSTRAINT reports_reason_len CHECK (char_length(reason) BETWEEN 1 AND 1000)
);

CREATE UNIQUE INDEX reports_open_unique ON reports (reporter_id, target_type, target_id)
WHERE
    status = 'open';

CREATE INDEX reports_status_created ON reports (status, created_at DESC, id DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id UUID NOT NULL,
    reason TEXT,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_events_action_check CHECK (
        action IN (
            'lock_user',
            'unlock_user',
            'change_role',
            'hide_content',
            'unhide_content',
            'resolve_report',
            'generate_fixtures',
            'reset_fixtures'
        )
    ),
    CONSTRAINT audit_events_target_type_check CHECK (
        target_type IN ('user', 'post', 'comment', 'event', 'media', 'report', 'fixtures')
    )
);

CREATE INDEX audit_events_at ON audit_events (at DESC, id DESC);
CREATE INDEX audit_events_actor ON audit_events (actor_id, at DESC);

ALTER TABLE comments
ADD COLUMN hidden_reason TEXT;

ALTER TABLE media
ADD COLUMN hidden_at TIMESTAMPTZ;

ALTER TABLE media
ADD COLUMN hidden_reason TEXT;
