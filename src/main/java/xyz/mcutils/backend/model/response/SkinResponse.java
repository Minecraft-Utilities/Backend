package xyz.mcutils.backend.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import xyz.mcutils.backend.config.Config;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.HashMap;
import java.util.Map;

public class SkinResponse extends Skin {
    /**
     * The URLs for the skin parts.
     */
    @JsonProperty("parts")
    private Map<String, String> urls;

    public SkinResponse(Skin skin) {
        super(skin.getUrl(), skin.getModel());
    }

    /**
     * Populates the part URLs for the skin.
     *
     * @param playerUuid the player's UUID
     */
    public void populatePartUrls(String playerUuid) {
        this.urls = new HashMap<>();
        for (Enum<?>[] type : ISkinPart.TYPES) {
            for (Enum<?> part : type) {
                ISkinPart skinPart = (ISkinPart) part;
                if (skinPart.hidden()) {
                    continue;
                }
                urls.put(part.name(), Config.INSTANCE.getWebPublicUrl() + "/player/" + playerUuid + "/skin/" + part.name().toLowerCase() + ".png");
            }
        }
    }
}
