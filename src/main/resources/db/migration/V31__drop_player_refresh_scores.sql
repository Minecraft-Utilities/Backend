DROP INDEX IF EXISTS idx_players_priority_score;
ALTER TABLE players DROP COLUMN IF EXISTS priority_score;
ALTER TABLE players DROP COLUMN IF EXISTS change_score;
ALTER TABLE players DROP COLUMN IF EXISTS last_changed;
