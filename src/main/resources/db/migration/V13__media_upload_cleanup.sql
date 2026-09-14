ALTER TABLE media ADD COLUMN upload_cleaned_at TIMESTAMPTZ;
CREATE INDEX media_upload_cleanup_idx ON media (created_at)
    WHERE upload_cleaned_at IS NULL AND status IN ('ready', 'rejected');
