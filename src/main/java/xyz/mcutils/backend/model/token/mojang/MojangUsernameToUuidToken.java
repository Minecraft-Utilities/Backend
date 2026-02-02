package xyz.mcutils.backend.model.token.mojang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class MojangUsernameToUuidToken {

    /**
     * The UUID of the player.
     */
    @JsonProperty("id")
    private String uuid;

    /**
     * The name of the player.
     */
    @JsonProperty("name")
    private String username;
}
