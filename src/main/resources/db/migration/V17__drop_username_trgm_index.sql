-- Remove the unused case-insensitive username trigram index.
DROP INDEX IF EXISTS idx_players_username_upper_trgm;
