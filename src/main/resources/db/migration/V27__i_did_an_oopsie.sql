ALTER TABLE players DROP COLUMN last_change_at;
ALTER TABLE players ADD COLUMN last_changed TIMESTAMP;