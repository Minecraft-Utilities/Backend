package xyz.mcutils.backend.model.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Full public representation of a tracked server.
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
 * @param firstSeen timestamp of the first successful discovery verification
 * @param lastCheckedAt timestamp of the latest refresh attempt
 * @param consecutiveOffline number of consecutive failed refreshes
 * @param motd server-advertised message of the day
 * @param latencyMs observed status-ping latency, if measured
 * @param modded whether the tracker identified the server as modded
 * @param preventsChatReports whether the server advertises chat-report prevention
 * @param enforcesSecureChat whether the server advertises enforced secure chat
 * @param previewsChat whether the server advertises chat previews
 * @param asn autonomous-system number, if known
 */
public record TrackedServerDetailResponse(
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
        Instant lastUpdated,
        Instant firstSeen,
        Instant lastCheckedAt,
        int consecutiveOffline,
        String motd,
        Integer latencyMs,
        boolean modded,
        boolean preventsChatReports,
        boolean enforcesSecureChat,
        boolean previewsChat,
        Long asn
) {
}
