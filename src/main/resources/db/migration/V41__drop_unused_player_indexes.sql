-- Dead indexes on the hottest write table (players): every refresh UPDATE pays for
-- these index entries but no query uses them.
--
--   idx_players_last_updated     (V3) named a method (findAllByLastUpdatedBeforeOrderByLastUpdatedAsc)
--                                that no longer exists; last_updated is only read as an entity field.
--   idx_players_monthly_views_full (V25) full index on monthly_views: resetMonthlyViews is served
--                                by the V24 partial index (WHERE monthly_views > 0), and
--                                updateMonthlyViews drives from its own aggregate subquery.
--
-- Verify with pg_stat_user_indexes before applying in production.
DROP INDEX IF EXISTS idx_players_last_updated;
DROP INDEX IF EXISTS idx_players_monthly_views_full;
