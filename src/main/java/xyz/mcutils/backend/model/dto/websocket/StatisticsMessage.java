package xyz.mcutils.backend.model.dto.websocket;

/**
 * The message for website statistics.
 *
 * @param playersTracked the amount of tracked players
 * @param trackedSkins   the amount of tracked skins
 */
public record StatisticsMessage(long playersTracked, long trackedSkins) {}
