package xyz.mcutils.backend.model.dto.response;

import java.util.Map;

/**
 * Public statistics of the internet server tracker.
 *
 * @param trackedServers total public servers in {@code tracker_servers} (honeypots excluded)
 * @param trackedPlayers distinct players ever seen on public, non-honeypot servers
 * @param onlinePlayers  distinct players currently present in the latest player sample of an
 *                       alive, non-honeypot server (verified: observed in a sample — the
 *                       server-advertised {@code online_count} is excluded because it can be falsified)
 * @param geo            top-10 country ISO code -> public server count
 * @param platform       top-10 public server-software (lowercased) -> server count, plain versions bucketed as {@code unknown}
 * @param protocol       top-10 public server status-protocol number -> server count
 */
public record TrackerStatsResponse(
        long trackedServers,
        long trackedPlayers,
        long onlinePlayers,
        Map<String, Long> geo,
        Map<String, Long> platform,
        Map<String, Long> protocol
) {}