-- Drop trigger-based unique_owners maintenance.
-- Replaced by application-level bulk UPDATE after batch inserts,
-- which amortises the per-row correlated subquery cost over the whole batch.
DROP TRIGGER IF EXISTS trg_skin_accounts_owned ON skin_change_events;
DROP FUNCTION IF EXISTS fn_skin_accounts_owned();

DROP TRIGGER IF EXISTS trg_cape_accounts_owned ON cape_change_events;
DROP FUNCTION IF EXISTS fn_cape_accounts_owned();
