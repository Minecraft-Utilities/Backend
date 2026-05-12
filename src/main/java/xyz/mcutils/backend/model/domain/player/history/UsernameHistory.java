package xyz.mcutils.backend.model.domain.player.history;

import java.time.Instant;

/**
 * A record representing a username history entry for a player.
 *
 * @param username  the username of the player
 * @param timestamp the timestamp of the username history entry
 */
public record UsernameHistory(String username, Instant timestamp) {}