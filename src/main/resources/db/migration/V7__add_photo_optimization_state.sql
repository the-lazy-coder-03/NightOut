ALTER TABLE photos ADD COLUMN optimization_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETE';
ALTER TABLE photos ADD COLUMN optimization_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE photos ADD COLUMN optimization_error VARCHAR(500);
ALTER TABLE photos ADD COLUMN optimization_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE photos ADD COLUMN optimized_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_photos_optimization_status ON photos(optimization_status);
