-- V49: public tracker API visibility and replacement player index.
-- Public reads exclude honeypots. The partial server index serves list ordering;
-- detail uses the primary key, and the replacement player index serves sightings.

CREATE INDEX idx_tracker_servers_public_last_updated_uuid
    ON tracker_servers (last_updated DESC, uuid ASC)
    WHERE honeypot = FALSE;

DROP INDEX IF EXISTS idx_tracker_player_history_player;

CREATE INDEX idx_tracker_player_history_player_seen
    ON tracker_player_history (player_uuid, last_seen DESC, server_uuid ASC);
