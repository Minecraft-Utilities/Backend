package xyz.mcutils.backend.model.dto.response;

import java.time.Instant;

/**
 * Public sighting of a tracked player on one server.
 *
 * @param username last username observed for the player on this server
 * @param firstSeen first sighting on this server
 * @param lastSeen latest sighting on this server
 * @param timesSeen number of samples in which the player was observed
 * @param server public summary of the server
 */
public record TrackedPlayerServerResponse(
        String username,
        Instant firstSeen,
        Instant lastSeen,
        int timesSeen,
        TrackedServerSummaryResponse server
) {
}
