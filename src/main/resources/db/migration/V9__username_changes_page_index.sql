-- Supports the recent global name changes page:
-- SELECT ... FROM username_change_events WHERE previous_username IS NOT NULL ORDER BY timestamp DESC LIMIT ?
-- Partial index excludes initial-username records (previous_username IS NULL), keeping the index small.
CREATE INDEX idx_username_change_events_changes_timestamp
    ON username_change_events (timestamp DESC)
    WHERE previous_username IS NOT NULL;
