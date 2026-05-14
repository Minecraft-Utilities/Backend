-- Foreign key indexes (PostgreSQL does not auto-index FK columns)
CREATE INDEX idx_players_skin_id ON players (skin_id);
CREATE INDEX idx_players_cape_id ON players (cape_id);

-- Refresh scheduling: findAllByLastUpdatedBeforeOrderByLastUpdatedAsc
CREATE INDEX idx_players_last_updated ON players (last_updated);

-- Skin change event lookups + trigger: WHERE skin_id = ? AND player_id = ?
-- Composite (player_id, skin_id) also covers findByPlayerId queries
CREATE INDEX idx_skin_change_events_player_skin ON skin_change_events (player_id, skin_id);

-- Cape change event lookups + trigger: WHERE cape_id = ? AND player_id = ?
-- Composite (player_id, cape_id) also covers findByPlayerId queries
CREATE INDEX idx_cape_change_events_player_cape ON cape_change_events (player_id, cape_id);

-- Username history: findByPlayerIdOrderByTimestampDesc
CREATE INDEX idx_username_change_events_player_timestamp ON username_change_events (player_id, timestamp DESC);

-- Pagination ordering: findAllOrderByUniqueOwnersDescIdAsc
CREATE INDEX idx_skins_unique_owners_id ON skins (unique_owners DESC, id ASC);
CREATE INDEX idx_capes_unique_owners_id ON capes (unique_owners DESC, id ASC);
