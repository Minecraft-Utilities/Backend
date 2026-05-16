-- Migration to create an index on skin_change_events for player_id and timestamp
CREATE INDEX idx_sce_player_id_timestamp ON skin_change_events (player_id, timestamp);