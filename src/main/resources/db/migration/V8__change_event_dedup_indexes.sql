CREATE UNIQUE INDEX idx_username_change_events_dedup
    ON username_change_events (player_id, new_username, COALESCE(previous_username, ''), date_trunc('day', timestamp AT TIME ZONE 'UTC'));

CREATE UNIQUE INDEX idx_skin_change_events_dedup
    ON skin_change_events (player_id, skin_id, date_trunc('day', timestamp AT TIME ZONE 'UTC'));

CREATE UNIQUE INDEX idx_cape_change_events_dedup
    ON cape_change_events (player_id, cape_id, date_trunc('day', timestamp AT TIME ZONE 'UTC'));
