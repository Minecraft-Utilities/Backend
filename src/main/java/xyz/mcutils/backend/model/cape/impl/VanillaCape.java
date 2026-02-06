package xyz.mcutils.backend.model.cape.impl;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.model.cape.CapeRendererType;
import xyz.mcutils.backend.service.CapeService;

import java.util.HashMap;
import java.util.Map;

@Getter @NoArgsConstructor
public class VanillaCape extends Cape {
    private String name;

    public VanillaCape(String name, String id) {
        super(
                id,
                "https://textures.minecraft.net/texture/" + id,
                AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(id),
                buildParts(id)
        );
        this.name = name;
    }

    /**
     * Builds the parts map (render type name -> URL) for this cape id.
     */
    public static Map<String, String> buildParts(String capeId) {
        Map<String, String> parts = new HashMap<>();
        String base = "%s/capes/%s".formatted(AppConfig.INSTANCE.getWebPublicUrl(), capeId);
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
        return CapeService.INSTANCE.getCapes().get(id);
    }

    /**
     * Creates a cape from its texture id
     *
     * @param id the texture id
     * @return the cape
     */
    public static Cape fromId(String id) {
        return CapeService.INSTANCE.getCapes().get(id);
    }
}
