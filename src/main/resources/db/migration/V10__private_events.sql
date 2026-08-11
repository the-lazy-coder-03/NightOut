CREATE TABLE private_events (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    event_name VARCHAR(180) NOT NULL,
    event_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location VARCHAR(255),
    join_code VARCHAR(8) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_private_events_creator ON private_events(creator_id);
CREATE INDEX idx_private_events_date ON private_events(event_date);

CREATE TABLE private_event_memberships (
    id BIGSERIAL PRIMARY KEY,
    private_event_id BIGINT NOT NULL REFERENCES private_events(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_private_event_membership UNIQUE (private_event_id, user_id)
);

CREATE INDEX idx_private_event_memberships_user ON private_event_memberships(user_id);
