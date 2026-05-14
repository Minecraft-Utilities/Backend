CREATE OR REPLACE FUNCTION fn_skin_accounts_owned()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Only count the first time a player appears with this skin
    IF NOT EXISTS (
        SELECT 1 FROM skin_change_events
        WHERE skin_id = NEW.skin_id
          AND player_id = NEW.player_id
          AND id != NEW.id
    ) THEN
        UPDATE skins SET unique_owners = unique_owners + 1 WHERE id = NEW.skin_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_skin_accounts_owned
AFTER INSERT ON skin_change_events
FOR EACH ROW EXECUTE FUNCTION fn_skin_accounts_owned();

CREATE OR REPLACE FUNCTION fn_cape_accounts_owned()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Only count the first time a player appears with this cape
    IF NOT EXISTS (
        SELECT 1 FROM cape_change_events
        WHERE cape_id = NEW.cape_id
          AND player_id = NEW.player_id
          AND id != NEW.id
    ) THEN
        UPDATE capes SET unique_owners = unique_owners + 1 WHERE id = NEW.cape_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cape_accounts_owned
AFTER INSERT ON cape_change_events
FOR EACH ROW EXECUTE FUNCTION fn_cape_accounts_owned();
