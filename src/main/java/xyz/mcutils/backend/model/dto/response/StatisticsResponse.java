package xyz.mcutils.backend.model.dto.response;

/**
 * Statistics about MC Utils.
 *
 * @param seenPlayers the amount of players in the database
 * @param seenSkins the amount of skins in the database
 * @param seenCapes the amount of capes in the database
 */
public record StatisticsResponse(long seenPlayers, long seenSkins, long seenCapes) { }
