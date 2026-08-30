-- #61: posts, post images, likes, and unique reposts (FS-POST-01..08).
CREATE TABLE posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body TEXT,
    visibility TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ,
    hidden_reason TEXT,
    CONSTRAINT posts_visibility_check CHECK (visibility IN ('friends', 'public')),
    CONSTRAINT posts_body_len CHECK (body IS NULL OR char_length(body) BETWEEN 1 AND 2000)
);

CREATE INDEX posts_author_created ON posts (author_id, created_at DESC, id DESC);
CREATE INDEX posts_created ON posts (created_at DESC, id DESC);

CREATE TABLE post_media (
    post_id UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media (id),
    position SMALLINT NOT NULL,
    PRIMARY KEY (post_id, position),
    CONSTRAINT post_media_position_range CHECK (position BETWEEN 0 AND 3)
);

CREATE UNIQUE INDEX post_media_media_id_unique ON post_media (media_id);

CREATE TABLE reposts (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX reposts_post_created ON reposts (post_id, created_at DESC);

CREATE TABLE likes (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, target_type, target_id),
    CONSTRAINT likes_target_type_check CHECK (target_type IN ('post', 'comment'))
);

CREATE INDEX likes_target ON likes (target_type, target_id, created_at DESC, user_id DESC);
