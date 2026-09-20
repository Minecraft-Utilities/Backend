-- Drop the online-count time series; the current online count is still kept on
-- tracker_servers (online_count / max_players), so only the per-sample history table
-- and its cascade target are removed.
DROP TABLE tracker_server_online_history;