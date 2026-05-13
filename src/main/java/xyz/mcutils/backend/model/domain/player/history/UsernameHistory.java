package xyz.mcutils.backend.model.domain.player.history;

import java.time.Instant;

/**
 * A record representing a username history entry for a player.
 *
 * @param username  the username of the player
 * @param firstSeen the time this username was first seen on the player
 * @param lastUsed  the time this username was last seen on the player
 */
public record UsernameHistory(String username, Instant firstSeen, Instant lastUsed) {}