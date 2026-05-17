CREATE INDEX IF NOT EXISTS idx_cape_change_events_cape_id_timestamp
ON cape_change_events (cape_id, timestamp ASC);

CREATE INDEX IF NOT EXISTS idx_skin_change_events_skin_id_timestamp
ON skin_change_events (skin_id, timestamp ASC);