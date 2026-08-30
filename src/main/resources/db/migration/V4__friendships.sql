-- #60: friend requests, accepted pairs, and blocks (FS-FRND-01..08).
-- Unordered pair uniqueness cannot be a table UNIQUE constraint (LEAST/GREATEST
-- are expressions). PostgreSQL expression unique index instead.
CREATE TABLE friendships (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT friendships_not_self CHECK (requester_id <> addressee_id),
    CONSTRAINT friendships_status_check CHECK (status IN ('pending', 'accepted', 'declined', 'blocked'))
);

CREATE UNIQUE INDEX friendships_pair_unique
    ON friendships ((LEAST(requester_id, addressee_id)), (GREATEST(requester_id, addressee_id)));

CREATE INDEX friendships_requester_status ON friendships (requester_id, status);
CREATE INDEX friendships_addressee_status ON friendships (addressee_id, status);
CREATE INDEX friendships_status_created ON friendships (status, created_at DESC, id DESC);
