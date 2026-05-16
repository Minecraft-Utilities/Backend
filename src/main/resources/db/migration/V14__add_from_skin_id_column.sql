-- Migration to add the 'from_skin_id' column to the 'skin_change_events' table
ALTER TABLE skin_change_events
ADD COLUMN from_skin_id BIGINT,
ADD CONSTRAINT fk_from_skin FOREIGN KEY (from_skin_id) REFERENCES skins (id);