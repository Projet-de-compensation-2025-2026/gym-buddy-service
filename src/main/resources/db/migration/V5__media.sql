-- #68: disk-safe media metadata. Bytes live in MinIO, not a local uploads/ dir.
CREATE TABLE media (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    mime TEXT NOT NULL,
    bytes BIGINT NOT NULL,
    variant_bytes BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    object_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT media_kind_check CHECK (kind IN ('avatar', 'post', 'message', 'event')),
    CONSTRAINT media_mime_check CHECK (
        mime IN ('image/jpeg', 'image/png', 'image/webp', 'audio/webm', 'audio/mpeg')
    ),
    CONSTRAINT media_status_check CHECK (status IN ('pending', 'ready', 'rejected', 'deleted')),
    CONSTRAINT media_bytes_positive CHECK (bytes > 0)
);

CREATE INDEX media_owner_active ON media (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX media_pending_created ON media (created_at) WHERE status = 'pending';
CREATE INDEX media_deleted_at ON media (deleted_at) WHERE deleted_at IS NOT NULL;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_avatar_media_fk
    FOREIGN KEY (avatar_media_id) REFERENCES media (id) ON DELETE SET NULL;
