package xyz.mcutils.backend.model.skin;

import com.google.gson.JsonObject;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.Texture;
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
@EqualsAndHashCode(callSuper = false)
public class Skin extends Texture {
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
     * The parts of the skin
     */
    @Setter
    private Map<String, String> parts;

    public Skin(String url, Model model, Player player) {
        String[] skinUrlParts = url.split("/");
        String textureId = skinUrlParts[skinUrlParts.length - 1];
        super(
                textureId,
                "https://textures.minecraft.net/texture/" + textureId,
                AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(textureId)
        );

        this.model = model;
        this.legacy = Skin.isLegacySkin(this);

        this.parts = new HashMap<>();
        for (SkinRendererType type : SkinRendererType.values()) {
            this.parts.put(type.name(), "%s/skins/%s/%s.png".formatted(
                AppConfig.INSTANCE.getWebPublicUrl(),
                player.getUniqueId(),
                type.name().toLowerCase()
            ));
        }
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
