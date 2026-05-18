-- Add first discoverer UUID to catalog rows.
-- Set only on first INSERT; never overwritten on conflict.
-- No FK: skins/capes are inserted before the player row exists (skin is needed to create the player).
-- first_seen_using_player_id is an informational attribution; the read path handles missing players via ifPresent.
ALTER TABLE skins ADD COLUMN first_seen_using_player_id UUID;
ALTER TABLE capes ADD COLUMN first_seen_using_player_id UUID;

-- Per-player adoption records.
-- One row per (player, skin/cape); first_seen is immutable after insert.
-- last_equipped_at is updated via ON CONFLICT upsert whenever the player re-equips.
CREATE TABLE player_skin_adoptions (
    player_id        UUID        NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    skin_id          BIGINT      NOT NULL REFERENCES skins(id),
    first_seen       TIMESTAMPTZ NOT NULL,
    last_equipped_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (player_id, skin_id)
);

CREATE TABLE player_cape_adoptions (
    player_id        UUID        NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    cape_id          BIGINT      NOT NULL REFERENCES capes(id),
    first_seen       TIMESTAMPTZ NOT NULL,
    last_equipped_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (player_id, cape_id)
);

-- Drop old accounts_owned triggers (they fired on skin/cape_change_events).
DROP TRIGGER IF EXISTS trg_skin_accounts_owned ON skin_change_events;
DROP TRIGGER IF EXISTS trg_cape_accounts_owned ON cape_change_events;
DROP FUNCTION IF EXISTS fn_skin_accounts_owned;
DROP FUNCTION IF EXISTS fn_cape_accounts_owned;

-- New triggers: increment unique_owners once per new (player, skin/cape) adoption.
-- UPDATE rows (re-equips) never fire this trigger.
CREATE OR REPLACE FUNCTION fn_skin_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE skins SET unique_owners = unique_owners + 1 WHERE id = NEW.skin_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_skin_adoption_unique_owners
AFTER INSERT ON player_skin_adoptions
FOR EACH ROW EXECUTE FUNCTION fn_skin_adoption_unique_owners();

CREATE OR REPLACE FUNCTION fn_cape_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE capes SET unique_owners = unique_owners + 1 WHERE id = NEW.cape_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cape_adoption_unique_owners
AFTER INSERT ON player_cape_adoptions
FOR EACH ROW EXECUTE FUNCTION fn_cape_adoption_unique_owners();

-- Drop event tables and sequences entirely.
-- CASCADE removes all attached indexes, FK constraints, and dependent objects.
DROP TABLE IF EXISTS skin_change_events CASCADE;
DROP TABLE IF EXISTS cape_change_events CASCADE;
DROP SEQUENCE IF EXISTS skin_change_events_seq;
DROP SEQUENCE IF EXISTS cape_change_events_seq;

-- Trending heat: distinct players who equipped this skin in the last 7 days.
-- Partial index keyed on skin_id + last_equipped_at; player_id INCLUDEd to cover the aggregation.
CREATE INDEX idx_skin_adoptions_skin_last_equipped
    ON player_skin_adoptions (skin_id, last_equipped_at DESC)
    INCLUDE (player_id);

-- Covering index for top-skins pagination by unique owners (includes new column).
CREATE INDEX idx_skins_unique_owners_id_covering ON skins (unique_owners DESC, id ASC)
    INCLUDE (texture_id, model, legacy, trending_heat, first_seen, first_seen_using_player_id);
