-- #67: private direct conversations and messages (FS-MSG-01..10).
-- One unordered pair per conversation. V9 is reserved for events (#64).
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    user_lo UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_hi UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT conversations_not_self CHECK (user_lo <> user_hi),
    CONSTRAINT conversations_pair_order CHECK (user_lo < user_hi),
    CONSTRAINT conversations_pair_unique UNIQUE (user_lo, user_hi)
);

CREATE INDEX conversations_user_lo ON conversations (user_lo);
CREATE INDEX conversations_user_hi ON conversations (user_hi);

CREATE TABLE conversation_reads (
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_read_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    body TEXT,
    media_id UUID REFERENCES media (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT messages_type_check CHECK (type IN ('text', 'image', 'audio'))
);

CREATE INDEX messages_conversation_created ON messages (conversation_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX messages_media_id_unique ON messages (media_id) WHERE media_id IS NOT NULL;
