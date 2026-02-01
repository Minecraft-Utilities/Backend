package xyz.mcutils.backend.model.player;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.*;
import xyz.mcutils.backend.config.Config;

@AllArgsConstructor @NoArgsConstructor
@Getter @EqualsAndHashCode @ToString
public class Cape {
    /**
     * The ID of the cape
     */
    @JsonIgnore private String id;

    /**
     * The texture URL to the cape.
     */
    private String textureUrl;

    /**
     * Creates a cape from an {@link JsonObject}.
     *
     * @param json the JSON object
     * @return the cape
     */
    public static Cape fromJson(JsonObject json, Player player) {
        if (json == null) {
            return null;
        }
        String url = json.get("url").getAsString();
        String[] capeUrlParts = url.split("/");

        String id = capeUrlParts[capeUrlParts.length - 1];
        return new Cape(
                id,
                Config.INSTANCE.getWebPublicUrl() + "/cape/texture/" + player.getUniqueId().toString() + ".png"
        );
    }

    /**
     * Creates a cape from its texture id
     *
     * @param id the texture id
     * @return the cape
     */
     public static Cape fromId(String id) {
        return new Cape(
                id,
                Config.INSTANCE.getWebPublicUrl() + "/cape/texture/" + id
        );
    }

    /**
     * Gets the Mojang texture URL for this skin.
     *
     * @return the Mojang texture URL for the skin
     */
    @JsonIgnore
    public String getMojangTextureUrl() {
        return "https://textures.minecraft.net/texture/" + this.id;
    }
}
