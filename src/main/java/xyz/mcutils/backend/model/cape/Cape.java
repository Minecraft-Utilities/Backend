package xyz.mcutils.backend.model.cape;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.*;
import xyz.mcutils.backend.config.AppConfig;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
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
     * The parts of the cape (render type name -> URL to rendered image).
     */
    @Setter private Map<String, String> parts;

    public Cape(String id) {
        this.id = id;
        this.textureUrl = AppConfig.INSTANCE.getWebPublicUrl() + "/cape/%s/texture.png".formatted(id);
        this.parts = buildParts(id);
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

    /**
     * Builds the parts map (render type name -> URL) for this cape id.
     */
    private static Map<String, String> buildParts(String capeId) {
        Map<String, String> parts = new HashMap<>();
        String base = "%s/cape/%s".formatted(AppConfig.INSTANCE.getWebPublicUrl(), capeId);
        for (CapeRendererType type : CapeRendererType.values()) {
            parts.put(type.name(), "%s/%s.png".formatted(base, type.name().toLowerCase()));
        }
        return parts;
    }

    /**
     * Creates a cape from an {@link JsonObject}.
     *
     * @param json the JSON object
     * @return the cape
     */
    public static Cape fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        String url = json.get("url").getAsString();
        String[] capeUrlParts = url.split("/");

        String id = capeUrlParts[capeUrlParts.length - 1];
        return new Cape(id);
    }

    /**
     * Creates a cape from its texture id
     *
     * @param id the texture id
     * @return the cape
     */
    public static Cape fromId(String id) {
        return new Cape(id);
    }
}
