-- MOTDs can legitimately exceed 1024 characters for JSON-component descriptions or
-- long legacy strings; a hard VARCHAR(1024) made whole flush batches fail with
-- "value too long for type character varying(1024)". TEXT has no length limit, and
-- the application still caps motd at a sane size before writing.
ALTER TABLE tracker_servers ALTER COLUMN motd TYPE TEXT;