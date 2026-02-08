package xyz.mcutils.backend.model.domain.cape.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.impl.cape.VanillaCapeRenderer;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Getter @Slf4j
@NoArgsConstructor
public class VanillaCape extends Cape<VanillaCape.Part> {
    private static final String CDN_URL = "https://textures.minecraft.net/texture/%s";

    /**
     * The UUID of this skin.
     */
    @JsonIgnore
    private UUID uuid;

    /**
     * The name of this cape.
     */
    private String name;

    public VanillaCape(UUID uuid, String name, String textureId) {
        super(
                textureId,
                CDN_URL.formatted(textureId),
                AppConfig.INSTANCE.getWebPublicUrl() + "/skins/%s/texture.png".formatted(textureId),
                buildParts(textureId)
        );
        this.uuid = uuid;
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
     * Creates a cape from its texture id
     *
     * @param id the texture id
     * @return the cape
     */
    public static VanillaCape fromId(String id) {
        return CapeService.INSTANCE.getCapes().get(id);
    }

    /**
     * Checks if a Vanilla cape exists for the given player
     *
     * @param textureId the player's name to check for
     * @return a future that returns true or false
     */
    public static CompletableFuture<Boolean> capeExists(String textureId) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Checking if Vanilla cape exists for player {}", textureId);
            String cdnUrl = CDN_URL.formatted(textureId);
            HttpResponse<byte[]> response;
            try {
                response = Constants.HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create(cdnUrl))
                                .HEAD()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | InterruptedException e) {
                return false;
            }
            return response.statusCode() == 200;
        });
    }

    @Getter
    public enum Part {
        FRONT(VanillaCapeRenderer.INSTANCE);

        private final Renderer<VanillaCape> renderer;

        Part(Renderer<VanillaCape> renderer) {
            this.renderer = renderer;
        }
    }
}
