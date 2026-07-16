ALTER TABLE players
    ADD COLUMN change_velocity DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN next_refresh_at TIMESTAMPTZ;

UPDATE players SET next_refresh_at = last_updated + INTERVAL '3 hours';

ALTER TABLE players ALTER COLUMN next_refresh_at SET NOT NULL;

CREATE INDEX idx_players_next_refresh_at ON players (next_refresh_at);
