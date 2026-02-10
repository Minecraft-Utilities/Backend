package xyz.mcutils.backend.model.dto.response;

import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.UUID;

/**
 * Minimal player info for the player search endpoint.
 *
 * @param id the player's uuid
 * @param username the player's username
 * @param skin the player's skin
 */
public record PlayerSearchEntry(UUID id, String username, Skin skin) { }
