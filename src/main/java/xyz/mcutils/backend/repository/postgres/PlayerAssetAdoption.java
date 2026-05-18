package xyz.mcutils.backend.repository.postgres;

import java.util.UUID;

/**
 * A single player-to-asset adoption row for batch inserts.
 */
public record PlayerAssetAdoption(UUID playerId, long assetId) {}
