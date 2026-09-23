package xyz.mcutils.backend.model.dto.response;

import java.util.Map;

/**
 * Public statistics of the internet server tracker.
 *
 * @param trackedServers total tracked servers
 * @param trackedPlayers distinct players observed by the tracker
 * @param onlinePlayers  distinct players present in the latest player sample of an alive server
 *                       (verified from observed samples rather than the server-advertised count)
 * @param geo            top-10 country ISO code -> server count
 * @param platform       top-10 server-software (lowercased) -> server count, plain versions bucketed as {@code unknown}
 * @param protocol       top-10 status-protocol number -> server count
 */
public record TrackerStatsResponse(
        long trackedServers,
        long trackedPlayers,
        long onlinePlayers,
        Map<String, Long> geo,
        Map<String, Long> platform,
        Map<String, Long> protocol
) {}