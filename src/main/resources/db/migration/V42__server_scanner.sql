-- Internet server scanner: discovered servers + resumable scan progress.
CREATE TABLE IF NOT EXISTS scanned_servers (
    ip           VARCHAR(45)  NOT NULL,
    port         INTEGER      NOT NULL,
    first_seen   TIMESTAMPTZ  NOT NULL,
    last_seen    TIMESTAMPTZ  NOT NULL,
    sample_count INTEGER      NOT NULL DEFAULT 0,
    honeypot     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (ip, port)
);

CREATE INDEX IF NOT EXISTS idx_scanned_servers_ip ON scanned_servers (ip);

CREATE TABLE IF NOT EXISTS scan_progress (
    id             INTEGER      PRIMARY KEY,
    seed           BIGINT       NOT NULL,
    permuted16_pos INTEGER      NOT NULL DEFAULT 0,
    offset24       INTEGER      NOT NULL DEFAULT 0,
    state          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    started_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL
);

INSERT INTO scan_progress (id, seed, permuted16_pos, offset24, state, updated_at)
VALUES (1, 0, 0, 0, 'RUNNING', now())
ON CONFLICT (id) DO NOTHING;