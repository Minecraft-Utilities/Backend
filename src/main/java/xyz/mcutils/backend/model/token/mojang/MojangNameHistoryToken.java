package xyz.mcutils.backend.model.token.mojang;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry of a player's Mojang name history.
 *
 * @param name        the username
 * @param changedToAt epoch millis when this name was adopted; {@code null} for the account's original name
 */
public record MojangNameHistoryToken(@JsonProperty("name") String name, @JsonProperty("changedToAt") Long changedToAt) {}