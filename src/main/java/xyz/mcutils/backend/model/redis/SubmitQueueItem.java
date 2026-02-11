package xyz.mcutils.backend.model.redis;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Queue payload for the player submit queue (Redis list).
 *
 * @param id  player UUID
 * @param submittedBy  the UUID of the player who submitted the players
 */
public record SubmitQueueItem(UUID id, @Nullable UUID submittedBy) {}
