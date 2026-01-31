package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.repository.PlayerSkinPartCacheRepository;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Log4j2(topic = "Skin Service")
public class SkinService {
    public static SkinService INSTANCE;

    private final PlayerSkinPartCacheRepository skinPartRepository;
    private final StorageService minioService;

    private final Cache<String, byte[]> skinCache =  CacheBuilder.newBuilder()
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
    public byte[] getSkinImage(Skin skin) {
        return this.skinCache.asMap().computeIfAbsent(skin.getId(), _ -> {
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
    }

    /**
     * Gets a skin part from the player's skin.
     *
     * @param skin the players skin
     * @param partName the name of the part
     * @param renderOverlay whether to render the overlay
     * @return the skin part
     */
    public CachedPlayerSkinPart getSkinPart(Skin skin, String partName, boolean renderOverlay, int size) {
        if (size > 512) {
            throw new BadRequestException("Size must not be larger than 512");
        }
        if (size < 32) {
            throw new BadRequestException("Size must not be smaller than 32");
        }

        ISkinPart part = ISkinPart.getByName(partName); // The skin part to get
        if (part == null) {
            throw new BadRequestException("Invalid skin part: '%s'".formatted(partName));
        }

        String name = part.name();
        log.debug("Getting skin part {} for texture: {} (size: {}, overlays: {})", name, skin.getId(), size, renderOverlay);
        String key = "%s-%s-%s-%s".formatted(skin.getId(), name, size, renderOverlay);

        Optional<CachedPlayerSkinPart> cache = skinPartRepository.findById(key);

        // The skin part is cached
        if (cache.isPresent() && AppConfig.isProduction()) {
            log.debug("Skin part {} for texture {} is cached", name, skin.getId());
            return cache.get();
        }

        long before = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(skin, renderOverlay, size); // Render the skin part
        log.debug("Took {}ms to render skin part {} for texture: {}", System.currentTimeMillis() - before, name, skin.getId());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(
                key,
                ImageUtils.imageToBytes(renderedPart)
        );
        log.debug("Fetched skin part {} for texture: {}", name, skin.getId());
        skinPartRepository.save(skinPart);
        return skinPart;
    }
}
