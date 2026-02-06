package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.SkinUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinRendererType;
import xyz.mcutils.backend.repository.PlayerSkinPartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SkinService {
    public static SkinService INSTANCE;

    @Value("${mc-utils.renderer.skin.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.skin.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.skin.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.skin.limits.max_size}")
    private int maxPartSize;

    private final PlayerSkinPartCacheRepository skinPartRepository;
    private final StorageService minioService;

    @Autowired
    public SkinService(PlayerSkinPartCacheRepository skinPartRepository, StorageService minioService) {
        this.skinPartRepository = skinPartRepository;
        this.minioService = minioService;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param skin the skin to get the image for
     * @return the skin image
     */
    public byte[] getSkinTexture(Skin skin, boolean upgrade) {
        byte[] skinBytes = minioService.get(StorageService.Bucket.SKINS, skin.getTextureId() + ".png");
        if (skinBytes == null) {
            log.debug("Downloading skin image for skin {}", skin.getTextureId());
            skinBytes = PlayerUtils.getImage(skin.getRawTextureUrl());
            if (skinBytes == null) {
                throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(skin.getTextureId()));
            }
            minioService.upload(StorageService.Bucket.SKINS, skin.getTextureId() + ".png", MediaType.IMAGE_PNG_VALUE, skinBytes);
            log.debug("Saved skin image for skin {}", skin.getTextureId());
        }
        return upgrade ? SkinUtils.upgradeLegacySkin(skin, skinBytes) : skinBytes;
    }

    /**
     * Gets a skin image from bytes.
     *
     * @param skinBytes the skin bytes
     * @return the skin image
     */
    public static BufferedImage getSkinImage(byte[] skinBytes) throws IOException {
        BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(skinBytes));
        if (skinImage == null) {
            throw new IllegalStateException("Failed to load skin image");
        }
        return skinImage;
    }

    /**
     * Renders a skin type from the player's skin.
     *
     * @param skin the player to get the skin for
     * @param typeName the name of the type
     * @param renderOverlay whether to render the overlay
     * @param size the output size (height; width derived per type)
     * @return the skin part
     */
    public CachedPlayerSkinPart renderSkin(Skin skin, String typeName, boolean renderOverlay, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Skin rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid skin part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        SkinRendererType part = SkinRendererType.getByName(typeName);
        if (part == null) {
            throw new BadRequestException("Invalid skin part: '%s'".formatted(typeName));
        }
        String name = part.name();
        String key = "%s-%s-%s-%s".formatted(skin.getTextureId(), name, size, renderOverlay);

        log.debug("Getting skin part for skin texture: {} (part {}, size {})", skin.getTextureId(), typeName, size);

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerSkinPart> cache = skinPartRepository.findById(key);
            if (cache.isPresent()) {
                log.debug("Got skin part for skin texture {} from cache in {}ms", skin.getTextureId(), System.currentTimeMillis() - cacheStart);
                return cache.get();
            }
        }

        long renderStart = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(skin, renderOverlay, size);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);
        log.debug("Took {}ms to render skin part for skin texture: {}", System.currentTimeMillis() - renderStart, skin.getTextureId());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(key, pngBytes);

        // don't save to cache in development
        if (cacheEnabled) {
            CompletableFuture.runAsync(() -> skinPartRepository.save(skinPart), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for skin part {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return skinPart;
    }
}
