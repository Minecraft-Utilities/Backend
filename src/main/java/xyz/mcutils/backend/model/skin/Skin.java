package xyz.mcutils.backend.model.skin;

import com.google.gson.JsonObject;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.config.Config;

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
    @Setter private boolean legacy;

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
        return Config.INSTANCE.getWebPublicUrl() + "/skin/" + this.id + ".png";
    }

    /**
     * Gets the Mojang texture URL for this skin.
     *
     * @return the Mojang texture URL for the skin
     */
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
