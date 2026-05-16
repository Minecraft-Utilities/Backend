-- Trending heat: periodically-updated count of distinct adopters within a rolling window.
-- Stored on the row for O(1) sort; recomputed hourly by a scheduled job.
ALTER TABLE skins ADD COLUMN trending_heat INTEGER NOT NULL DEFAULT 0;

-- Pagination ordering: sort by trending_heat DESC, id ASC (tiebreak)
CREATE INDEX idx_skins_trending_heat_id ON skins (trending_heat DESC, id ASC);
