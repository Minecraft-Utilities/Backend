-- last_equipped_at is NULL until the player actually changes to this skin/cape (not on first DB track).
-- Trending heat only counts rows where last_equipped_at is set.
ALTER TABLE player_skin_adoptions ALTER COLUMN last_equipped_at DROP NOT NULL;
ALTER TABLE player_cape_adoptions ALTER COLUMN last_equipped_at DROP NOT NULL;

DROP INDEX IF EXISTS idx_skin_adoptions_skin_last_equipped;
CREATE INDEX idx_skin_adoptions_skin_last_equipped
    ON player_skin_adoptions (skin_id, last_equipped_at DESC)
    INCLUDE (player_id)
    WHERE last_equipped_at IS NOT NULL;
