ALTER TABLE clubs ADD COLUMN image_storage_file_id VARCHAR(191);
ALTER TABLE clubs ADD COLUMN image_mime_type VARCHAR(120);
ALTER TABLE clubs ADD COLUMN image_file_size BIGINT;
ALTER TABLE clubs ADD COLUMN image_uploaded_at TIMESTAMP WITH TIME ZONE;
