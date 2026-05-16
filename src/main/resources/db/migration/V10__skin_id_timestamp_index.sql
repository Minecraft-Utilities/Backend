-- findFirstBySkinId: WHERE skin_id = ? ORDER BY timestamp ASC LIMIT 1
-- No existing index has skin_id as leading column, causing full table scans
CREATE INDEX idx_skin_change_events_skin_id_timestamp ON skin_change_events (skin_id, timestamp);

-- Superseded by idx_skin_change_events_dedup which leads with (player_id, skin_id)
DROP INDEX idx_skin_change_events_player_skin;

-- 0 scans: PostgreSQL routes findFirstTimestampsBySkinIds through the dedup index instead
DROP INDEX idx_skin_change_events_player_id_skin_id_timestamp;
