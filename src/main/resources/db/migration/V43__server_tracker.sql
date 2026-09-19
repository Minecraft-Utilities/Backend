-- Server tracker: telemetry dataset (tracked servers, player history, online history).
ALTER TABLE scan_progress RENAME TO tracker_progress;

CREATE TABLE tracked_servers (
    uuid                   UUID        PRIMARY KEY,
    ip                     VARCHAR(45) NOT NULL,
    port                   INTEGER     NOT NULL,
    first_seen             TIMESTAMPTZ NOT NULL,
    last_updated           TIMESTAMPTZ NOT NULL,
    online_count           INTEGER     NOT NULL DEFAULT 0,
    max_players            INTEGER     NOT NULL DEFAULT 0,
    version                VARCHAR(64),
    protocol               INTEGER,
    platform               VARCHAR(32),
    motd                   VARCHAR(1024),
    motd_hash              BYTEA,
    favicon_hash           BYTEA,
    modded                 BOOLEAN     NOT NULL DEFAULT FALSE,
    latency_ms             INTEGER,
    prevents_chat_reports  BOOLEAN     NOT NULL DEFAULT FALSE,
    enforces_secure_chat   BOOLEAN     NOT NULL DEFAULT FALSE,
    previews_chat          BOOLEAN     NOT NULL DEFAULT FALSE,
    country                VARCHAR(2),
    asn                    BIGINT,
    sample_count           INTEGER     NOT NULL DEFAULT 0,
    honeypot               BOOLEAN     NOT NULL DEFAULT FALSE,
    consecutive_offline    INTEGER     NOT NULL DEFAULT 0,
    last_refreshed         TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ 'epoch',
    UNIQUE (ip, port)
);

-- Refresh cycle: least-recently-refreshed first; every server equal
CREATE INDEX idx_tracked_servers_refresh ON tracked_servers (last_refreshed);

CREATE TABLE player_history (
    server_uuid UUID        NOT NULL REFERENCES tracked_servers(uuid) ON DELETE CASCADE,
    player_uuid UUID        NOT NULL,
    username    VARCHAR(16) NOT NULL,
    first_seen  TIMESTAMPTZ NOT NULL,
    last_seen   TIMESTAMPTZ NOT NULL,
    times_seen  INTEGER     NOT NULL DEFAULT 1,
    PRIMARY KEY (server_uuid, player_uuid)
);
CREATE INDEX idx_player_history_player ON player_history (player_uuid);
CREATE INDEX idx_player_history_username ON player_history (username);

CREATE TABLE server_online_history (
    server_uuid UUID        NOT NULL REFERENCES tracked_servers(uuid) ON DELETE CASCADE,
    sampled_at  TIMESTAMPTZ NOT NULL,
    online      INTEGER     NOT NULL,
    max         INTEGER     NOT NULL,
    version     VARCHAR(64),
    PRIMARY KEY (server_uuid, sampled_at)
);

-- Backfill old scanner rows into the telemetry table, then drop the legacy table.
INSERT INTO tracked_servers (uuid, ip, port, first_seen, last_updated, sample_count, honeypot)
SELECT gen_random_uuid(), ip, port, first_seen, last_seen, sample_count, honeypot
FROM scanned_servers;

DROP TABLE scanned_servers;