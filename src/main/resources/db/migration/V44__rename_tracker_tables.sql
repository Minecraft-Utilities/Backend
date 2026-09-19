-- Server tracker dataset tables share a tracker_ prefix so the feature's tables are
-- discoverable together. Constraint/index names derived from the table names
-- (tracked_servers_pkey, player_history_server_uuid_fkey, ...) ride along automatically;
-- the explicitly named idx_* indexes are renamed to match.
ALTER TABLE tracked_servers RENAME TO tracker_servers;
ALTER TABLE player_history RENAME TO tracker_player_history;
ALTER TABLE server_online_history RENAME TO tracker_server_online_history;

ALTER INDEX idx_tracked_servers_refresh RENAME TO idx_tracker_servers_refresh;
ALTER INDEX idx_player_history_player RENAME TO idx_tracker_player_history_player;
ALTER INDEX idx_player_history_username RENAME TO idx_tracker_player_history_username;