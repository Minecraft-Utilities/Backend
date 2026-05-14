-- Case-insensitive LIKE search: WHERE UPPER(username) LIKE UPPER(?)
-- The plain expression index from V5 only covers equality; a leading wildcard
-- (%query%) requires trigram support to avoid a full sequential scan.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_players_username_upper_trgm ON players USING GIN (UPPER(username) gin_trgm_ops);
