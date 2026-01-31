package xyz.mcutils.backend.model.skin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.config.Config;
import xyz.mcutils.backend.service.SkinService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
     * The texture URL to the skin
     */
    @Setter private String textureUrl;

    /**
     * The parts of the skin
     */
    @Setter private Map<String, String> parts;

    public Skin(String url, Model model) {
        this.model = model;
        this.parts = new HashMap<>();

        String[] skinUrlParts = url.split("/");
        this.id = skinUrlParts[skinUrlParts.length - 1];
        this.populateSkinData();
        this.legacy = Skin.isLegacySkin(this);
    }

    /**
     * Populates the skin data for a player.
     */
    public void populateSkinData() {
        this.setTextureUrl(Config.INSTANCE.getWebPublicUrl() + "/skin/texture/%s.png".formatted(this.id));

        for (Enum<?>[] types : ISkinPart.TYPES) {
            for (Enum<?> enumValue : types) {
                ISkinPart part = (ISkinPart) enumValue;
                if (part.hidden()) {
                    continue;
                }
                this.parts.put(part.name(), "%s/skin/%s/%s.png".formatted(
                        Config.INSTANCE.getWebPublicUrl(),
                        this.id,
                        part.name().toLowerCase()
                ));
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
     * Gets a skin from its texture id.
     * This is only used for getting skin image.
     *
     * @param id the texture id
     * @return the skin
     */
    public static Skin fromId(String id) {
        return new Skin(
                id,
                Model.DEFAULT // Doesn't matter in this case
        );
    }

    @SneakyThrows
    private static boolean isLegacySkin(Skin skin) {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(SkinService.INSTANCE.getSkinImage(skin)));
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
