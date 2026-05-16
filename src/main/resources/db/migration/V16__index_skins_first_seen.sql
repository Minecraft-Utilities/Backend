-- Add an index on the first_seen column in the skins table
CREATE INDEX idx_skins_first_seen ON skins (first_seen);