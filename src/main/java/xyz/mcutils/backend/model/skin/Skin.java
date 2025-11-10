package xyz.mcutils.backend.model.skin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.config.Config;
import xyz.mcutils.backend.model.player.Player;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor @NoArgsConstructor
@Getter @Log4j2(topic = "Skin") @EqualsAndHashCode
public class Skin {
    /**
     * The ID for the skin
     */
    @JsonIgnore private String id;

    /**
     * The model for the skin
     */
    private Model model;

    /**
     * The legacy status of the skin
     */
    @Setter private boolean legacy;

    /**
     * The URL to the skin
     */
    @Setter private String url;

    /**
     * The parts of the skin
     */
    @Setter private Map<String, String> parts;

    public Skin(String url, Model model) {
        this.model = model;
        this.parts = new HashMap<>();

        String[] skinUrlParts = url.split("/");
        this.id = skinUrlParts[skinUrlParts.length - 1];
    }

    /**
     * Populates the skin data for a player.
     *
     * @param player the player to populate the skin data for
     */
    public void populateSkinData(Player player) {
        this.setUrl(Config.INSTANCE.getWebPublicUrl() + "/skin/" + player.getUniqueId() + ".png");

        for (Enum<?>[] types : ISkinPart.TYPES) {
            for (Enum<?> enumValue : types) {
                ISkinPart part = (ISkinPart) enumValue;
                if (part.hidden()) {
                    continue;
                }
                this.parts.put(part.name(), Config.INSTANCE.getWebPublicUrl() + "/player/" + player.getUniqueId() + "/skin/" + part.name().toLowerCase() + ".png");
            }
        }
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
     * Gets the skin from a {@link JsonObject}.
     *
     * @param json the JSON object
     * @return the skin
     */
    public static Skin fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        String url = json.get("url").getAsString();
        JsonObject metadata = json.getAsJsonObject("metadata");
        return new Skin(
                url,
                EnumUtils.getEnumConstant(Model.class, metadata != null ? metadata.get("model").getAsString().toUpperCase() : "DEFAULT")
        );
    }

    /**
     * The model of the skin.
     */
    public enum Model {
        DEFAULT,
        SLIM
    }
}
