-- #63: unique id on reposts so the friends feed can cursor on activity time+id (FS-FEED-03).
ALTER TABLE reposts
    ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE reposts
    ADD CONSTRAINT reposts_id_key UNIQUE (id);

CREATE INDEX reposts_user_created ON reposts (user_id, created_at DESC, id DESC);
