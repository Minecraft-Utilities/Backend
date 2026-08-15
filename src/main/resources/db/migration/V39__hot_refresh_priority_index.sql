-- Hot-priority refresh selection index.
-- The refresh loop claims "hot" players (change_velocity >= 0.5 OR monthly_views > 0) first,
-- ordered by change_velocity DESC, so the most volatile players are refreshed at their adaptive
-- cadence even when the loop is overloaded (a plain next_refresh_at FIFO flattens everyone to
-- the same effective rate at 60M+ tracked players).
--
-- Predicate MUST stay in sync with PlayerRefreshSchedule.HOT_VELOCITY_THRESHOLD and the JPQL
-- literals in PlayerRepository.findHotDueForRefreshSkippable / findColdDueForRefreshSkippable.
CREATE INDEX idx_players_hot_refresh_priority
    ON players (change_velocity DESC, monthly_views DESC, next_refresh_at ASC)
    WHERE change_velocity >= 0.5 OR monthly_views > 0;
