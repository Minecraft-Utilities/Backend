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
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.cache.CachedPlayerCapePart;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.model.cape.CapeData;
import xyz.mcutils.backend.model.cape.CapeRendererType;
import xyz.mcutils.backend.repository.PlayerCapePartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service @Slf4j
public class CapeService {
    public static CapeService INSTANCE;

    private static final Map<String, CapeData> capes = new HashMap<>();
    static {
        List<CapeData> capeData = new ArrayList<>();
        capeData.add(new CapeData("Migrator", "2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"));

        for (CapeData data : capeData) {
            capes.put(data.textureId(), data);
        }
    }

    @Value("${mc-utils.renderer.cape.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.cape.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.cape.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.cape.limits.max_size}")
    private int maxPartSize;

    private final StorageService minioService;
    private final PlayerCapePartCacheRepository capePartRepository;

    @Autowired
    public CapeService(StorageService minioService, PlayerCapePartCacheRepository capePartRepository) {
        this.minioService = minioService;
        this.capePartRepository = capePartRepository;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets all the known capes.
     *
     * @return the known capes
     */
    public Map<String, CapeData> getCapes() {
        return Collections.unmodifiableMap(capes);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param cape the skin to get the image for
     * @return the skin image
     */
    public byte[] getCapeTexture(Cape cape) {
        byte[] capeBytes = minioService.get(StorageService.Bucket.CAPES, cape.getId() + ".png");
        if (capeBytes == null) {
            log.debug("Downloading skin image for skin {}", cape.getId());
            capeBytes = PlayerUtils.getImage(cape.getMojangTextureUrl());
            if (capeBytes == null) {
                throw new IllegalStateException("Cape with id '%s' was not found".formatted(cape.getId()));
            }
            minioService.upload(StorageService.Bucket.CAPES, cape.getId() + ".png", MediaType.IMAGE_PNG_VALUE, capeBytes);
            log.debug("Saved cape image for skin {}", cape.getId());
        }
        return capeBytes;
    }

    /**
     * Gets a cape image from bytes.
     *
     * @param capeBytes the cape bytes
     * @return the cape image
     */
    public BufferedImage getCapeImage(byte[] capeBytes) throws IOException {
        BufferedImage capeImage = ImageIO.read(new ByteArrayInputStream(capeBytes));
        if (capeImage == null) {
            throw new IllegalStateException("Failed to load cape image");
        }
        return capeImage;
    }

    /**
     * Renders a cape part at the given size.
     *
     * @param cape the cape to render
     * @param typeName the cape part type (e.g. FRONT), see {@link CapeRendererType#getByName(String)}
     * @param size the output size (height; width derived from cape aspect)
     * @return the cached cape part (PNG bytes)
     */
    public CachedPlayerCapePart renderCape(Cape cape, String typeName, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Cape rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid cape part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        CapeRendererType part = CapeRendererType.getByName(typeName);
        if (part == null) {
            throw new BadRequestException("Invalid cape part: '%s'".formatted(typeName));
        }

        String key = "%s-%s-%s".formatted(cape.getId(), part.name(), size);

        log.debug("Getting cape part for cape: {} (part {}, size {})", cape.getId(), typeName, size);

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerCapePart> cache = capePartRepository.findById(key);
            if (cache.isPresent()) {
                log.debug("Got cape part for cape {} from cache in {}ms", cape.getId(), System.currentTimeMillis() - cacheStart);
                return cache.get();
            }
        }

        long renderStart = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(cape, size);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);
        log.debug("Took {}ms to render cape part for cape: {}", System.currentTimeMillis() - renderStart, cape.getId());

        CachedPlayerCapePart capePart = new CachedPlayerCapePart(key, pngBytes);

        if (cacheEnabled) {
            CompletableFuture.runAsync(() -> capePartRepository.save(capePart), Main.EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Save failed for cape part {}: {}", key, ex.getMessage());
                    return null;
                });
        }
        return capePart;
    }
}
