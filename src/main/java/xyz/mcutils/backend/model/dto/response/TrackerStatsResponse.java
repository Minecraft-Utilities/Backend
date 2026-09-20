package xyz.mcutils.backend.model.dto.response;

import java.util.Map;

/**
 * Public statistics of the internet server tracker.
 *
 * @param trackedServers       total servers in {@code tracker_servers} (incl. honeypot-flagged)
 * @param trackedPlayers       distinct players ever seen across all tracked servers
 * @param verifiedOnlinePlayers distinct players currently present in the latest player sample of an
 *                              alive, non-honeypot server (observed in a sample — the server-advertised
 *                              {@code online_count} is excluded because it can be falsified)
 * @param geo                  top-10 country ISO code -> server count
 * @param platform             top-10 server-software (lowercased) -> server count, plain versions bucketed as {@code unknown}
 * @param protocol             top-10 status-protocol number -> server count
 */
public record TrackerStatsResponse(
        long trackedServers,
        long trackedPlayers,
        long verifiedOnlinePlayers,
        Map<String, Long> geo,
        Map<String, Long> platform,
        Map<String, Long> protocol
) {}