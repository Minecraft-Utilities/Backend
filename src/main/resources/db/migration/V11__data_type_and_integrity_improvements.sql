-- 1. Fix texture_id storage: Minecraft texture hashes are always exactly 64 hex chars.
--    VARCHAR(64) enforces the length constraint; Hibernate maps String fields to varchar.
ALTER TABLE skins ALTER COLUMN texture_id TYPE VARCHAR(64);
ALTER TABLE capes ALTER COLUMN texture_id TYPE VARCHAR(64);

-- 2. Fix model storage: native PostgreSQL enum is stored as a 4-byte OID instead of
--    varchar, saving ~4 bytes per row. @JdbcType(PostgreSQLEnumJdbcType.class) on the
--    entity field makes Hibernate schema validation accept this type.
CREATE TYPE skin_model AS ENUM ('DEFAULT', 'SLIM');
ALTER TABLE skins ALTER COLUMN model TYPE skin_model USING model::skin_model;

-- 3. Fix username lengths: Minecraft's hard limit is 16 characters
ALTER TABLE players ALTER COLUMN username TYPE VARCHAR(16);
ALTER TABLE username_change_events ALTER COLUMN new_username TYPE VARCHAR(16);
ALTER TABLE username_change_events ALTER COLUMN previous_username TYPE VARCHAR(16);

-- 4. Add missing FK constraints on player_id in all three event tables
--    (player_id was previously an unbound UUID column with no referential integrity)
ALTER TABLE skin_change_events
    ADD CONSTRAINT fk_skin_change_events_player_id
    FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE;

ALTER TABLE cape_change_events
    ADD CONSTRAINT fk_cape_change_events_player_id
    FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE;

ALTER TABLE username_change_events
    ADD CONSTRAINT fk_username_change_events_player_id
    FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE;

-- 5. Allow cape_id to be NULL so an unequip event can be recorded
ALTER TABLE cape_change_events ALTER COLUMN cape_id DROP NOT NULL;

-- 6. The existing V8 dedup index on (player_id, cape_id, day) excludes NULL cape_id rows
--    because PostgreSQL unique indexes treat NULLs as distinct. Add a dedicated partial
--    index to deduplicate unequip events at the same day granularity.
CREATE UNIQUE INDEX idx_cape_change_events_unequip_dedup
    ON cape_change_events (player_id, date_trunc('day', timestamp AT TIME ZONE 'UTC'))
    WHERE cape_id IS NULL;
