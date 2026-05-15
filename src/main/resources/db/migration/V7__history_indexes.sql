-- Replace skin/cape history indexes now that queries filter by player_id first
DROP INDEX idx_skin_change_events_skin_id_timestamp;
DROP INDEX idx_cape_change_events_cape_id_timestamp;

-- Skin history: MIN(timestamp) FROM skin_change_events WHERE player_id = ? AND skin_id IN (...) GROUP BY skin_id
CREATE INDEX idx_skin_change_events_player_id_skin_id_timestamp ON skin_change_events (player_id, skin_id, timestamp);

-- Cape history: MIN(timestamp) FROM cape_change_events WHERE player_id = ? AND cape_id IN (...) GROUP BY cape_id
CREATE INDEX idx_cape_change_events_player_id_cape_id_timestamp ON cape_change_events (player_id, cape_id, timestamp);
