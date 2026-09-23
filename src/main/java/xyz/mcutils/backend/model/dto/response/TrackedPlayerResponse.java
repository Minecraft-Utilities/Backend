package xyz.mcutils.backend.model.dto.response;

import xyz.mcutils.backend.common.Pagination;

import java.util.UUID;

/**
 * Public tracked-player resource and its paginated server sightings.
 *
 * @param playerUuid player UUID whose history is requested
 * @param servers page of public sightings
 */
public record TrackedPlayerResponse(
        UUID playerUuid,
        Pagination.Page<TrackedPlayerServerResponse> servers
) {
}
