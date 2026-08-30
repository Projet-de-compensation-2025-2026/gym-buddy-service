-- #62: nested comments (FS-CMT-01..07). Likes already allow target_type=comment.
CREATE TABLE comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id UUID REFERENCES comments (id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    depth SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ,
    CONSTRAINT comments_depth_range CHECK (depth BETWEEN 0 AND 4),
    CONSTRAINT comments_body_len CHECK (char_length(body) BETWEEN 1 AND 1000),
    CONSTRAINT comments_root_depth CHECK (
        (parent_id IS NULL AND depth = 0)
        OR (parent_id IS NOT NULL AND depth BETWEEN 1 AND 4)
    )
);

CREATE INDEX comments_post_roots ON comments (post_id, created_at DESC, id DESC)
WHERE
    parent_id IS NULL;

CREATE INDEX comments_parent ON comments (parent_id, created_at ASC, id ASC);

CREATE INDEX comments_post ON comments (post_id);
