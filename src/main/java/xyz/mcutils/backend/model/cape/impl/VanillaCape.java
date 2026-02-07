package xyz.mcutils.backend.model.cape.impl;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.impl.cape.VanillaCapeRenderer;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@NoArgsConstructor
public class VanillaCape extends Cape<VanillaCape.Part> {

    @Getter
    public enum Part {
        FRONT(VanillaCapeRenderer.INSTANCE);

        private final Renderer<VanillaCape> renderer;

        Part(Renderer<VanillaCape> renderer) {
            this.renderer = renderer;
        }
    }

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

    @Override
    public Set<Part> getSupportedParts() {
        return EnumSet.of(Part.FRONT);
    }

    @Override
    public Part fromPartName(String name) {
        return EnumUtils.getEnumConstant(Part.class, name);
    }

    @Override
    public BufferedImage render(Part part, int size, RenderOptions options) {
        return part.getRenderer().render(this, size, options);
    }

    /**
     * Builds the parts map (render type name -> URL) for this cape id.
     */
    public static Map<String, String> buildParts(String capeId) {
        Map<String, String> parts = new HashMap<>();
        String base = "%s/capes/vanilla/%s".formatted(AppConfig.INSTANCE.getWebPublicUrl(), capeId);
        for (Part p : Part.values()) {
            parts.put(p.name(), "%s/%s.png".formatted(base, p.name().toLowerCase()));
        }
        return parts;
    }

    /**
     * Creates a cape from an {@link JsonObject}.
     *
     * @param json the JSON object
     * @return the cape
     */
    public static VanillaCape fromJson(JsonObject json) {
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
    public static VanillaCape fromId(String id) {
        return CapeService.INSTANCE.getCapes().get(id);
    }
}
