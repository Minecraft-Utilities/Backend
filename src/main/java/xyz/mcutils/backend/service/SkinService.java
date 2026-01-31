package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
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

import javax.imageio.ImageIO;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
    public byte[] getSkinImage(Skin skin, boolean upgrade) {
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

        return upgrade ? upgradeLegacySkin(skin, skinBytes) : skinBytes;
    }

    /**
     * Upgrades a legacy 64×32 skin to 64×64 (1.8 format) if needed.
     *
     * @param skin the skin to upgrade
     * @return PNG bytes (64×64 if input was 64×32, otherwise unchanged)
     */
    private byte[] upgradeLegacySkin(Skin skin, byte[] skinImage) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(skinImage));
            if (image == null || image.getWidth() != 64 || image.getHeight() != 32) {
                return skinImage;
            }
            log.debug("Upgrading legacy skin '{}'", skin.getId());
            BufferedImage upgraded = upgradeLegacySkin(image);
            return ImageUtils.imageToBytes(upgraded);
        } catch (Exception e) {
            log.warn("Could not upgrade legacy skin, using original: {}", e.getMessage());
            return skinImage;
        }
    }

    /**
     * Upgrades a legacy 64×32 Minecraft skin to the modern 64×64 format (1.8+).
     * Legacy skins have no overlays — only base layer. Places the 64×32 in the top
     * half, mirrors the leg/arm to create the missing left leg and left arm base,
     * then clears all overlay regions to transparent.
     *
     * @param image the skin image (legacy 64×32 or already 64×64)
     * @return the image in 64×64 format
     */
    public static BufferedImage upgradeLegacySkin(@NotNull BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w == h) {
            return image;
        }
        if (h * 2 != w) {
            return image;
        }
        double scale = w / 64.0;
        int newH = h + (int) (32 * scale);
        BufferedImage upgraded = new BufferedImage(w, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = upgraded.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Create missing left leg and left arm (mirror from legacy right leg/arm)
        for (int[] rect : ISkinPart.Vanilla.LegacyUpgrade.LEFT_LEG_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }
        for (int[] rect : ISkinPart.Vanilla.LegacyUpgrade.LEFT_ARM_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }

        // Clear overlay regions (HEADZ, BODYZ, RAZ, LAZ, LLZ) — legacy skins have no overlays
        for (int[] region : ISkinPart.Vanilla.LegacyUpgrade.CLEAR_OVERLAYS) {
            ImageUtils.setAreaTransparentIfOpaque(upgraded, region[0], region[1], region[2], region[3], scale);
        }

        return upgraded;
    }

    /**
     * Gets a skin part from the player's skin.
     *
     * @param skin the players skin
     * @param partName the name of the part
     * @param renderOverlay whether to render the overlay
     * @param size the output size (height; width derived per part)
     * @return the skin part
     */
    public CachedPlayerSkinPart getSkinPart(Skin skin, String partName, boolean renderOverlay, int size) {
        if (size > 1024) {
            throw new BadRequestException("Size must not be larger than 1024");
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
