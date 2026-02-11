package xyz.mcutils.backend.model.domain.cape.impl;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.impl.cape.OptifineCapeRenderer;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.service.StorageService;

import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("HttpUrlsUsage")
@Getter @Slf4j
@NoArgsConstructor
public class OptifineCape extends Cape<OptifineCape.Part> {
    private static final String CDN_URL = "http://s.optifine.net/capes/%s.png";

    @Getter
    public enum Part {
        FRONT(OptifineCapeRenderer.INSTANCE);

        private final Renderer<OptifineCape> renderer;

        Part(Renderer<OptifineCape> renderer) {
            this.renderer = renderer;
        }
    }

    public OptifineCape(String playerName) {
        String cdnUrl = CDN_URL.formatted(playerName);
        super(
                playerName,
                cdnUrl,
                cdnUrl,
                buildParts(playerName)
        );
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
    public static Map<String, String> buildParts(String playerName) {
        Map<String, String> parts = new HashMap<>();
        String base = "%s/capes/optifine/%s".formatted(AppConfig.INSTANCE.getWebPublicUrl(), playerName);
        for (Part p : Part.values()) {
            parts.put(p.name(), "%s/%s.png".formatted(base, p.name().toLowerCase()));
        }
        return parts;
    }

    /**
     * Checks if an optifine cape exists for the given player
     *
     * @param playerName the player's name to check for
     * @param webRequest the HTTP client
     * @return a future that returns true or false
     */
    public static CompletableFuture<Boolean> capeExists(String playerName, WebRequest webRequest) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            boolean hasCape = StorageService.INSTANCE.exists(StorageService.Bucket.OPTIFINE_CAPES, playerName + ".png");
            if (!hasCape) {
                hasCape = webRequest.checkExists(CDN_URL.formatted(playerName));
            }
            log.debug("Optifine cape exists for player {}: {} in {}ms", playerName, hasCape, System.currentTimeMillis() - start);
            return hasCape;
        });
    }
}
