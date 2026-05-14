-- TopSubmittedPlayersMetric: ORDER BY submitted_uuids DESC FETCH FIRST 10
CREATE INDEX idx_players_submitted_uuids ON players (submitted_uuids DESC);

-- Skin history: MIN(timestamp) FROM skin_change_events WHERE skin_id IN (...) GROUP BY skin_id
CREATE INDEX idx_skin_change_events_skin_id_timestamp ON skin_change_events (skin_id, timestamp);

-- Cape history: MIN(timestamp) FROM cape_change_events WHERE cape_id IN (...) GROUP BY cape_id
CREATE INDEX idx_cape_change_events_cape_id_timestamp ON cape_change_events (cape_id, timestamp);

-- Never used: LOWER(username) varchar_pattern_ops only helps prefix LIKE; CONTAINING generates %query% (leading wildcard)
DROP INDEX idx_players_username_lower;
