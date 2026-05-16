-- Drop the existing index on sce_player_id_timestamp
DROP INDEX idx_sce_player_id_timestamp;

-- Create a new index on skin_change_events for skin_id and timestamp
-- This index applies only to rows where from_skin_id is not null
CREATE INDEX idx_sce_skin_id_timestamp_nonfirst ON skin_change_events (skin_id, timestamp)
WHERE from_skin_id IS NOT NULL;