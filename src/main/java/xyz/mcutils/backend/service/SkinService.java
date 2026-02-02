package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import xyz.mcutils.backend.model.skin.SkinPart;
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

    private static final int MIN_PART_SIZE = 64;
    private static final int MAX_PART_SIZE = 1024;

    private final PlayerSkinPartCacheRepository skinPartRepository;
    private final StorageService minioService;

    private final Cache<String, byte[]> skinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

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
     * Gets a skin part from the player's skin.
     *
     * @param player the player to get the skin for
     * @param partName the name of the part
     * @param renderOverlay whether to render the overlay
     * @param size the output size (height; width derived per part)
     * @return the skin part
     */
    public CachedPlayerSkinPart renderSkinPart(Player player, String partName, boolean renderOverlay, int size) {
        if (size <= MIN_PART_SIZE || size > MAX_PART_SIZE) {
            throw new BadRequestException("Invalid skin part size. Must be between " + MIN_PART_SIZE + " and " + MAX_PART_SIZE);
        }

        SkinPart part = SkinPart.getByName(partName);
        if (part == null) {
            throw new BadRequestException("Invalid skin part: '%s'".formatted(partName));
        }
        Skin skin = player.getSkin();
        String name = part.name();
        String key = "%s-%s-%s-%s".formatted(skin.getId(), name, size, renderOverlay);

        Optional<CachedPlayerSkinPart> cache = skinPartRepository.findById(key);
        if (cache.isPresent() && AppConfig.isProduction()) {
            return cache.get();
        }

        BufferedImage renderedPart = part.render(skin, renderOverlay, size);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(key, pngBytes);

        // don't save to cache in development
        if (AppConfig.isProduction()) {
            CompletableFuture.runAsync(() -> skinPartRepository.save(skinPart), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for skin part {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return skinPart;
    }
}
