-- Covering index for top-skins pagination by unique owners.
-- This allows PostgreSQL to satisfy the top-N query from the index without heap fetches
-- when the visibility map is sufficiently up-to-date.
CREATE INDEX idx_skins_unique_owners_id_covering ON skins (unique_owners DESC, id ASC)
    INCLUDE (texture_id, model, legacy, trending_heat, first_seen);
