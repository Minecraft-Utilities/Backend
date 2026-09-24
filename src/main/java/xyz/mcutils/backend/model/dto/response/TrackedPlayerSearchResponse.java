package xyz.mcutils.backend.model.dto.response;

import java.util.UUID;

/** Username-search result for a player with public server-tracker history. */
public record TrackedPlayerSearchResponse(UUID playerUuid, String username, long skinId) {}
