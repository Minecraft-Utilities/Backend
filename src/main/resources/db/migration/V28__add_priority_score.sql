ALTER TABLE players ADD COLUMN priority_score DOUBLE PRECISION NOT NULL DEFAULT 0;
CREATE INDEX idx_players_priority_score ON players (priority_score DESC);