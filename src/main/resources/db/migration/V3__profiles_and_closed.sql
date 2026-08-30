-- #59: remaining profile columns (FS-PROF-01, FS-PROF-02) and closed accounts (FS-ACCT-07).
ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('active', 'locked', 'pending_verification', 'closed'));

ALTER TABLE profiles
    ADD COLUMN bio TEXT,
    ADD COLUMN visibility TEXT NOT NULL DEFAULT 'public',
    ADD COLUMN sports TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN experience_level TEXT,
    ADD COLUMN city TEXT,
    ADD COLUMN lat DOUBLE PRECISION,
    ADD COLUMN lng DOUBLE PRECISION,
    ADD COLUMN preferred_windows JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN avatar_media_id UUID;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_visibility_check CHECK (visibility IN ('public', 'private'));

ALTER TABLE profiles
    ADD CONSTRAINT profiles_experience_check CHECK (
        experience_level IS NULL
        OR experience_level IN ('beginner', 'intermediate', 'advanced')
    );
