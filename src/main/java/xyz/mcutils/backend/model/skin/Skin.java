package xyz.mcutils.backend.model.skin;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.PlayerUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@AllArgsConstructor @NoArgsConstructor @Document("skins")
@Getter @Log4j2(topic = "Skin") @EqualsAndHashCode
public class Skin {
    /**
     * The ID for the skin
     */
    @Id private String id;

    /**
     * The model for the skin
     */
    private Model model;

    /**
     * The legacy status of the skin
     */
    private boolean legacy;

    public Skin(String url, Model model) {
        this.model = model;

        String[] skinUrlParts = url.split("/");
        this.id = skinUrlParts[skinUrlParts.length - 1];
    }

    /**
     * Gets the URL for this skin.
     *
     * @return the url for the skin
     */
    public String getUrl() {
        return "https://textures.minecraft.net/texture/" + this.id;
    }

    public byte[] getSkinImage() {
        byte[] skinImage = PlayerUtils.getSkinImage(this.getUrl());
        if (skinImage == null) {
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(skinImage));
            this.legacy = image.getWidth() == 64 && image.getHeight() == 32;
        } catch (Exception ignored) {}

        return null;
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
