ALTER TABLE private_events ADD COLUMN invite_token VARCHAR(80);

CREATE UNIQUE INDEX idx_private_events_invite_token ON private_events(invite_token);
