-- Performance: the hourly monthly-views job (PlayerRepository.resetMonthlyViews /
-- updateMonthlyViews) filters player_view_events by viewed_at over a 30-day window.
-- The existing (player_id, ip_address, viewed_at) index cannot serve a viewed_at-only
-- range scan, so both statements scanned the whole table every hour.
--
-- (player_id) is INCLUDE'd so the DISTINCT/GROUP BY lookups are index-only.
-- If this table is very large at deploy time, run this as
--   CREATE INDEX CONCURRENTLY ...;
-- outside of the Flyway transaction instead.
CREATE INDEX idx_player_view_events_viewed_at
    ON player_view_events (viewed_at) INCLUDE (player_id);
