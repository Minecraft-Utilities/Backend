package xyz.mcutils.backend.model.token.mojang;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param uuid     The UUID of the player.
 * @param username The name of the player.
 */
public record MojangUsernameToUuidToken(
        @JsonProperty("id") String uuid,
        @JsonProperty("name") String username
) {
}
