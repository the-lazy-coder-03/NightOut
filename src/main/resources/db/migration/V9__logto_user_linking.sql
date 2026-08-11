ALTER TABLE app_users ADD COLUMN logto_subject VARCHAR(255);
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE app_users ADD CONSTRAINT uk_app_users_logto_subject UNIQUE (logto_subject);
