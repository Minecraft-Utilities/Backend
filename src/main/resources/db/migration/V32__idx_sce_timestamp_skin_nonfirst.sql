-- Trending heat: scan recent non-first skin changes across all skins (see SkinRepository.updateTrendingHeat)
CREATE INDEX IF NOT EXISTS idx_sce_timestamp_skin_nonfirst
    ON skin_change_events (timestamp, skin_id)
    INCLUDE (player_id)
    WHERE from_skin_id IS NOT NULL;
