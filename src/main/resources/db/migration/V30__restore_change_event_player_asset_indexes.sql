-- Restores (player_id, skin_id) / (player_id, cape_id) lookups used by accounts-owned triggers
-- after dedup unique indexes were dropped in V29.

CREATE INDEX IF NOT EXISTS idx_skin_change_events_player_skin
    ON skin_change_events (player_id, skin_id);

CREATE INDEX IF NOT EXISTS idx_cape_change_events_player_cape
    ON cape_change_events (player_id, cape_id);
