package xyz.mcutils.backend.model.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact public representation of a tracked server.
 *
 * @param uuid stable tracker server identifier
 * @param ip server address
 * @param port server port
 * @param version server-advertised version, if supplied
 * @param protocol server-advertised status protocol, if supplied
 * @param platform server software, if identified
 * @param onlineCount server-advertised player count
 * @param maxPlayers server-advertised player capacity
 * @param country ISO country code, if known
 * @param online whether the latest refresh attempt succeeded
 * @param lastUpdated timestamp of the latest successful status fetch
 */
public record TrackedServerSummaryResponse(
        UUID uuid,
        String ip,
        int port,
        String version,
        Integer protocol,
        String platform,
        int onlineCount,
        int maxPlayers,
        String country,
        boolean online,
        Instant lastUpdated
) {
}
