-- V50: focused public tracker filter and sort indexes.
-- Honeypot rows are always excluded from public reads, so every index is partial.
-- B-tree indexes here serve exact, range, and order operations. The contains filters for
-- country/platform intentionally do not add separate indexes because leading wildcard
-- `LIKE` patterns cannot use B-tree indexes.

CREATE INDEX idx_tracker_servers_public_country_uuid
    ON tracker_servers (country, uuid)
    WHERE honeypot = FALSE;

CREATE INDEX idx_tracker_servers_public_platform_uuid
    ON tracker_servers (platform, uuid)
    WHERE honeypot = FALSE;

CREATE INDEX idx_tracker_servers_public_protocol_uuid
    ON tracker_servers (protocol, uuid)
    WHERE honeypot = FALSE;

CREATE INDEX idx_tracker_servers_public_online_count_uuid
    ON tracker_servers (online_count, uuid)
    WHERE honeypot = FALSE;

CREATE INDEX idx_tracker_servers_public_latency_ms_uuid
    ON tracker_servers (latency_ms, uuid)
    WHERE honeypot = FALSE;