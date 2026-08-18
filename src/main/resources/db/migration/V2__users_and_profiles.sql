-- Auth #12: account row + the profile created at registration (FS-ACCT-01, FS-ACCT-10).
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email CITEXT NOT NULL,
    handle CITEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_handle_unique UNIQUE (handle),
    CONSTRAINT users_role_check CHECK (role IN ('member', 'moderator', 'admin')),
    CONSTRAINT users_status_check CHECK (status IN ('active', 'locked', 'pending_verification'))
);

CREATE TABLE profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name TEXT NOT NULL
);
