ALTER TABLE clubs ADD COLUMN area VARCHAR(120);

UPDATE clubs
SET area = CASE
    WHEN LOWER(city) = 'claremont' THEN 'Claremont'
    WHEN LOWER(city) = 'stellenbosch' THEN 'Stellenbosch'
    ELSE 'Cape Town'
END;

ALTER TABLE clubs ALTER COLUMN area SET NOT NULL;

CREATE INDEX idx_clubs_active_area ON clubs(active, area);
