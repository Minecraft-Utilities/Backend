-- Range scan for "equips in the last N days" (hourly trending_heat rebuild).
-- Complements idx_skin_adoptions_skin_last_equipped (skin_id-first), which is poor for global time windows.
CREATE INDEX IF NOT EXISTS idx_skin_adoptions_last_equipped_recent
    ON player_skin_adoptions (last_equipped_at DESC)
    INCLUDE (skin_id, player_id)
    WHERE last_equipped_at IS NOT NULL;
