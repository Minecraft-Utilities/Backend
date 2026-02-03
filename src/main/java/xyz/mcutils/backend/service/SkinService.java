package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinRendererType;
import xyz.mcutils.backend.repository.PlayerSkinPartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkinService {
    public static SkinService INSTANCE;

    @Value("${mc-utils.renderer.skin.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.skin.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.skin.limits.max_size}")
    private int maxPartSize;

    private final PlayerSkinPartCacheRepository skinPartRepository;
    private final StorageService minioService;

    private final Cache<String, byte[]> skinCache;

    @Autowired
    public SkinService(@Value("${mc-utils.cache.ttl.skin-textures}") int cacheTtl, PlayerSkinPartCacheRepository skinPartRepository, StorageService minioService) {
        this.skinCache = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheTtl, TimeUnit.MINUTES)
                .build();
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
        byte[] skinBytes = this.skinCache.asMap().computeIfAbsent(skin.getId(), _ -> {
            byte[] skinImage = minioService.get(StorageService.Bucket.SKINS, skin.getId() + ".png");
            if (skinImage == null) {
                log.debug("Downloading skin image for skin {}", skin.getId());
                skinImage = PlayerUtils.getImage(skin.getMojangTextureUrl());
                if (skinImage == null) {
                    throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(skin.getId()));
                }
                minioService.upload(StorageService.Bucket.SKINS, skin.getId() + ".png", MediaType.IMAGE_PNG_VALUE, skinImage);
                log.debug("Saved skin image for skin {}", skin.getId());
            }
            return skinImage;
        });

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
     * @param player the player to get the skin for
     * @param typeName the name of the type
     * @param renderOverlay whether to render the overlay
     * @param size the output size (height; width derived per type)
     * @return the skin part
     */
    public CachedPlayerSkinPart renderSkin(Player player, String typeName, boolean renderOverlay, int size) {
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
        Skin skin = player.getSkin();
        String name = part.name();
        String key = "%s-%s-%s-%s".formatted(skin.getId(), name, size, renderOverlay);

        log.debug("Getting skin part for player: {} (part {}, size {})", player.getUsername(), typeName, size);

        long cacheStart = System.currentTimeMillis();
        if (AppConfig.INSTANCE.isCacheEnabled()) {
            Optional<CachedPlayerSkinPart> cache = skinPartRepository.findById(key);
            if (cache.isPresent()) {
                log.debug("Got skin part for {} from cache in {}ms", player.getUsername(), System.currentTimeMillis() - cacheStart);
                return cache.get();
            }
        }

        long renderStart = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(skin, renderOverlay, size);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);
        log.debug("Took {}ms to render skin part for player: {}", System.currentTimeMillis() - renderStart, player.getUsername());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(key, pngBytes);

        // don't save to cache in development
        if (AppConfig.INSTANCE.isCacheEnabled()) {
            CompletableFuture.runAsync(() -> skinPartRepository.save(skinPart), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for skin part {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return skinPart;
    }
}
