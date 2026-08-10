CREATE TABLE clubs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    city VARCHAR(120) NOT NULL,
    address VARCHAR(255),
    logo_url VARCHAR(500),
    storage_folder_id VARCHAR(191),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clubs_active ON clubs(active);

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    event_name VARCHAR(180) NOT NULL,
    event_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_club_date ON events(club_id, event_date DESC);
CREATE INDEX idx_events_date ON events(event_date);

CREATE TABLE photos (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    storage_file_id VARCHAR(191) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    safe_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_photos_event ON photos(event_id);
CREATE INDEX idx_photos_event_status ON photos(event_id, status);
CREATE INDEX idx_photos_uploaded_at ON photos(uploaded_at);

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_user_clubs (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, club_id)
);

CREATE INDEX idx_app_user_clubs_club ON app_user_clubs(club_id);
