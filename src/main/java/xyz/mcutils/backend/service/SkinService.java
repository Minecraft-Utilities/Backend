package xyz.mcutils.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.repository.mongo.SkinRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.redis.PlayerSkinPartCacheRepository;

@Service
@Log4j2(topic = "Skin Service")
public class SkinService {
    public static SkinService INSTANCE;

    private final SkinRepository skinRepository;
    private final PlayerRepository playerRepository;
    private final PlayerSkinPartCacheRepository skinPartRepository;
    private final StorageService minioService;

    @Autowired
    public SkinService(SkinRepository skinRepository, PlayerRepository playerRepository, PlayerSkinPartCacheRepository skinPartRepository, StorageService minioService) {
        this.skinRepository = skinRepository;
        this.playerRepository = playerRepository;
        this.skinPartRepository = skinPartRepository;
        this.minioService = minioService;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Creates a skin from a skin.
     *
     * @param skin the skin object
     * @return the skin
     */
    @SneakyThrows
    public Skin createSkin(Skin skin) {
        // Check if the skin already exists
        if (this.skinRepository.existsById(skin.getId())) {
            return skin;
        }

        // Get the skin image
        byte[] skinImage = this.getSkinImage(skin);

        // Check if the skin is legacy
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(skinImage));
        skin.setLegacy(image.getWidth() == 64 && image.getHeight() == 32);

        // Create the skin
        this.skinRepository.save(skin);
        return skin;
    }

    /**
     * Gets a skin for the given id.
     *
     * @param id the id of the skin
     * @return the skin
     */
    public Skin getSkin(String id) {
        return getSkin(id, null);
    }

    /**
     * Gets a skin for the given id.
     *
     * @param id the id of the skin
     * @param skin the json data to create the skin with (optional)
     * @return the skin
     */
    public Skin getSkin(String id, Skin skin) {
        Optional<Skin> optionalSkin = this.skinRepository.findById(id);
        if (optionalSkin.isEmpty() && skin != null) {
            return createSkin(skin);
        }

        if (optionalSkin.isEmpty()) {
            log.info("Skin {} not found", id);
            return null;
        }
        return optionalSkin.get();
    }

    /**
     * Checks if a skin exists.
     *
     * @param id the id of the skin
     * @return whether the skin exists
     */
    public boolean skinExists(String id) {
        return skinRepository.existsById(id);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param skin the skin to get the image for
     * @return the skin image
     */
    public byte[] getSkinImage(Skin skin) {
        byte[] skinImage = minioService.get(StorageService.Bucket.SKINS, skin.getId() + ".png");
        if (skinImage == null) {
            log.info("Downloading skin image for skin {}", skin.getId());
            skinImage = PlayerUtils.getSkinImage(skin.getMojangTextureUrl());
            if (skinImage == null) {
                throw new IllegalStateException("Skin image not found for skin " + skin.getId());
            }
            minioService.upload(StorageService.Bucket.SKINS, skin.getId() + ".png", skinImage);
            log.info("Saved skin image for skin {}", skin.getId());
        }

        return skinImage;
    }

    /**
     * Gets a skin part from the player's skin.
     *
     * @param player the player
     * @param partName the name of the part
     * @param renderOverlay whether to render the overlay
     * @return the skin part
     */
    public CachedPlayerSkinPart getSkinPart(Player player, String partName, boolean renderOverlay, int size) {
        if (size > 512) {
            throw new BadRequestException("Size cannot be larger than 512");
        }
        if (size < 32) {
            throw new BadRequestException("Size cannot be smaller than 32");
        }

        ISkinPart part = ISkinPart.getByName(partName); // The skin part to get
        if (part == null) {
            throw new BadRequestException("Invalid skin part: %s".formatted(partName));
        }

        String name = part.name();
        log.info("Getting skin part {} for player: {} (size: {}, overlays: {})", name, player.getUniqueId(), size, renderOverlay);
        String key = "%s-%s-%s-%s".formatted(player.getUniqueId(), name, size, renderOverlay);

        Optional<CachedPlayerSkinPart> cache = skinPartRepository.findById(key);

        // The skin part is cached
        if (cache.isPresent() && AppConfig.isProduction()) {
            log.info("Skin part {} for player {} is cached", name, player.getUniqueId());
            return cache.get();
        }

        long before = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(player.getCurrentSkin(), renderOverlay, size); // Render the skin part
        log.info("Took {}ms to render skin part {} for player: {}", System.currentTimeMillis() - before, name, player.getUniqueId());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(
                key,
                ImageUtils.imageToBytes(renderedPart)
        );
        log.info("Fetched skin part {} for player: {}", name, player.getUniqueId());
        skinPartRepository.save(skinPart);
        return skinPart;
    }

    /**
     * Gets the most popular skins based on current usage count.
     * Much more efficient: counts usage on player side, only fetches needed skins.
     *
     * @param limit the maximum number of skins to return
     * @return list of popular skins with their current usage counts
     */
    public List<PopularSkinInfo> getMostPopularSkins(int limit) {
        if (limit <= 0) {
            throw new BadRequestException("Limit must be greater than 0");
        }
        if (limit > 100) {
            throw new BadRequestException("Limit cannot be greater than 100");
        }

        // Get all current skin IDs from players (much faster than aggregation)
        List<Player> players = playerRepository.findAllCurrentSkinIds();
        
        // Count usage for each skin ID
        Map<String, Long> skinUsageCounts = players.stream()
            .filter(player -> player.getCurrentSkinId() != null)
            .collect(Collectors.groupingBy(
                Player::getCurrentSkinId,
                Collectors.counting()
            ));
        
        // Sort by usage count and get top N
        List<String> popularSkinIds = skinUsageCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
        
        // Only fetch the skins we actually need
        List<Skin> skins = skinRepository.findByIdIn(popularSkinIds);
        Map<String, Skin> skinMap = skins.stream()
            .collect(Collectors.toMap(Skin::getId, skin -> skin));
        
        // Build result in correct order
        return popularSkinIds.stream()
            .map(skinId -> {
                Skin skin = skinMap.get(skinId);
                Long count = skinUsageCounts.get(skinId);
                if (skin != null && count != null) {
                    return new PopularSkinInfo(skin, count.intValue());
                }
                return null;
            })
            .filter(info -> info != null)
            .toList();
    }

    /**
     * Information about a popular skin including current usage count
     */
    public static class PopularSkinInfo {
        private final Skin skin;
        private final int currentUsageCount;

        public PopularSkinInfo(Skin skin, int currentUsageCount) {
            this.skin = skin;
            this.currentUsageCount = currentUsageCount;
        }

        public Skin getSkin() {
            return skin;
        }

        public int getCurrentUsageCount() {
            return currentUsageCount;
        }
    }
}
