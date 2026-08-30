-- #66: friend suggestion dismissals, score cache, and weekly matching opt-in (FS-SUGG, FS-MATCH).
-- V9/V10 reserved for events/messaging on other tickets.

CREATE TABLE suggestion_dismissals (
    viewer_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    until TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (viewer_id, candidate_id),
    CONSTRAINT suggestion_dismissals_not_self CHECK (viewer_id <> candidate_id)
);

CREATE INDEX suggestion_dismissals_until ON suggestion_dismissals (until);

CREATE TABLE suggestion_scores (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL,
    primary_reason TEXT NOT NULL,
    mutual_friends INTEGER NOT NULL DEFAULT 0,
    computed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, candidate_id),
    CONSTRAINT suggestion_scores_not_self CHECK (user_id <> candidate_id)
);

CREATE INDEX suggestion_scores_user_rank ON suggestion_scores (user_id, score DESC, candidate_id);

CREATE TABLE matching_opt_ins (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, week_start)
);

CREATE TABLE matching_pairs (
    week_start DATE NOT NULL,
    user_a UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id UUID,
    activity TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    duration_min INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (week_start, user_a, user_b),
    CONSTRAINT matching_pairs_order CHECK (user_a < user_b),
    CONSTRAINT matching_pairs_not_self CHECK (user_a <> user_b),
    CONSTRAINT matching_pairs_duration CHECK (duration_min >= 1 AND duration_min <= 1440)
);

CREATE UNIQUE INDEX matching_pairs_week_user_a ON matching_pairs (week_start, user_a);
CREATE UNIQUE INDEX matching_pairs_week_user_b ON matching_pairs (week_start, user_b);
