CREATE TABLE private_event_photos (
    id BIGSERIAL PRIMARY KEY,
    private_event_id BIGINT NOT NULL REFERENCES private_events(id) ON DELETE CASCADE,
    uploaded_by_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    storage_file_id VARCHAR(191) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    safe_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_private_event_photos_event_uploaded ON private_event_photos(private_event_id, uploaded_at DESC);
CREATE INDEX idx_private_event_photos_uploaded_by ON private_event_photos(uploaded_by_id);
