ALTER TABLE players ADD COLUMN monthly_views BIGINT NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION fn_player_monthly_views()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE players SET monthly_views = monthly_views + 1 WHERE id = NEW.player_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_player_monthly_views
    AFTER INSERT ON player_view_events
    FOR EACH ROW EXECUTE FUNCTION fn_player_monthly_views();