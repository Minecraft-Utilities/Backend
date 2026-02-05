package xyz.mcutils.backend.model.skin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonObject;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.service.SkinService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor @NoArgsConstructor
@Getter
@Slf4j
@EqualsAndHashCode
public class Skin {
    /**
     * The ID for the skin
     */
    @JsonProperty("textureId")
    private String id;

    /**
     * The model for the skin
     */
    private Model model;

    /**
     * The legacy status of the skin
     */
    @Setter
    private boolean legacy;

    /**
     * The texture URL to the skin
     */
    @Setter
    private String textureUrl;

    /**
     * The parts of the skin
     */
    @Setter
    private Map<String, String> parts;

    public Skin(String url, Model model, Player player) {
        String[] skinUrlParts = url.split("/");
        this.id = skinUrlParts[skinUrlParts.length - 1];

        this.model = model;
        this.legacy = Skin.isLegacySkin(this);
        this.textureUrl = AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(player.getUniqueId().toString());

        this.parts = new HashMap<>();
        for (SkinRendererType type : SkinRendererType.values()) {
            this.parts.put(type.name(), "%s/skins/%s/%s.png".formatted(
                AppConfig.INSTANCE.getWebPublicUrl(),
                player.getUniqueId().toString(),
                type.name().toLowerCase()
            ));
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
    public static Skin fromJson(JsonObject json, Player player) {
        if (json == null) {
            return null;
        }
        String url = json.get("url").getAsString();
        JsonObject metadata = json.getAsJsonObject("metadata");
        return new Skin(
                url,
                EnumUtils.getEnumConstant(Model.class, metadata != null ? metadata.get("model").getAsString().toUpperCase() : "DEFAULT"),
                player
        );
    }

    @SneakyThrows
    private static boolean isLegacySkin(Skin skin) {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(SkinService.INSTANCE.getSkinTexture(skin, false)));
        if (image == null) {
            return false;
        }
        return image.getWidth() == 64 && image.getHeight() == 32;
    }

    /**
     * The model of the skin.
     */
    public enum Model {
        DEFAULT,
        SLIM
    }
}
