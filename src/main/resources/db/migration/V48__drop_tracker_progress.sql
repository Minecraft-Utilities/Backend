-- Discovery no longer persists a resumable cursor: the sweep walks the public IPv4 space in a
-- random order, and when a cycle finishes it reshuffles and starts the next one, forever.
DROP TABLE IF EXISTS tracker_progress;
