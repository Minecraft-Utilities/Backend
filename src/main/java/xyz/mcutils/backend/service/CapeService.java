package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.bson.Document;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.model.domain.cape.CapeType;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service @Slf4j
public class CapeService {
    public static CapeService INSTANCE;

    @Value("${mc-utils.renderer.cape.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.cape.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.cape.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.cape.limits.max_size}")
    private int maxPartSize;

    private final StorageService storageService;
    private final PlayerService playerService;
    private final CapeManager capeManager;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;

    private final Cache<String, byte[]> capeTextureCache = CacheBuilder.newBuilder()
            .expireAfterAccess(6, TimeUnit.HOURS)
            .maximumSize(1000)
            .build();

    public CapeService(StorageService storageService, @Lazy PlayerService playerService, CapeManager capeManager,
                       MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.storageService = storageService;
        this.playerService = playerService;
        this.capeManager = capeManager;
        this.mongoTemplate = mongoTemplate;
        this.webRequest = webRequest;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets all the known capes sorted by accounts owned.
     * Queries DB for cape IDs only, then resolves each via cache/manager.
     *
     * @return the known capes
     */
    public Map<String, VanillaCape> getCapes() {
        Map<String, VanillaCape> capes = new LinkedHashMap<>();
        Query q = new Query().with(Sort.by(Sort.Order.desc("accountsOwned"), Sort.Order.asc("_id")));
        List<Document> idDocs = MongoUtils.findWithFields(mongoTemplate, q, CapeDocument.class, "_id");
        for (Document doc : idDocs) {
            UUID id = doc.get("_id", UUID.class);
            capeManager.getById(id).map(this::fromDocument).ifPresent(c -> capes.put(c.getTextureId(), c));
        }
        return capes;
    }

    /**
     * Gets a cape by its id (cache or repository).
     *
     * @param id the cape document id
     * @return the cape, or null if not found
     */
    public VanillaCape getCapeById(UUID id) {
        if (id == null) {
            return null;
        }
        return this.capeManager.getById(id)
                .map(this::fromDocument)
                .orElse(null);
    }

    /**
     * Gets a cape by texture id (creates if valid and missing).
     *
     * @param textureId the cape to get
     * @return the cape
     * @throws NotFoundException if the cape does not exist and could not be created
     */
    public VanillaCape getCapeByTextureId(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return null;
        }
        return capeManager.getByTextureId(textureId)
                .map(this::fromDocument)
                .orElseGet(() -> fromDocument(capeManager.getOrCreateByTextureId(textureId)));
    }

    /**
     * Gets a Cape from the texture id or the player's name / uuid.
     *
     * @param query the query to search for
     * @return the cape, or null
     */
    public Cape<?> getCapeFromTextureIdOrPlayer(String query, CapeType type) {
        if (type == CapeType.OPTIFINE) {
            return new OptifineCape(query);
        }

        Cape<?> cape;
        // I really have no idea how long their sha-1 string length is
        // a player name can't be more than 16 chars, so just assume it's a texture id
        if (query.length() > 16) {
            cape = this.getCapeByTextureId(query);
        } else {
            Player player = this.playerService.getPlayer(query);
            cape = player.getCape();
            if (cape == null) {
                throw new NotFoundException("Player '%s' does not have a cape equipped".formatted(player.getUsername()));
            }
        }
        return cape;
    }

    /**
     * Increments accountsOwned in memory for the given cape.
     *
     * @param capeId the cape document id
     */
    public void incrementAccountsOwned(UUID capeId) {
        capeManager.incrementAccountsOwned(capeId, 1);
    }

    /**
     * Gets the cape image for the given cape.
     *
     * @param cape the cape to get the image for
     * @return the cape image
     */
    public byte[] getCapeTexture(Cape<?> cape) {
        byte[] capeBytes;
        try {
            long start = System.currentTimeMillis();
            capeBytes = this.capeTextureCache.get("%s-%s.png".formatted(cape.getClass().getName(), cape.getTextureId()), () -> {
                log.debug("Downloading cape image for skin {}", cape.getTextureId());
                byte[] bytes = webRequest.getAsByteArray(cape.getRawTextureUrl());
                if (bytes == null) {
                    throw new IllegalStateException("Cape image for skin '%s' was not found".formatted(cape.getTextureId()));
                }
                log.debug("Downloaded cape image for skin {} in {}ms", cape.getTextureId(),  System.currentTimeMillis() - start);
                return bytes;
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return capeBytes;
    }

    /**
     * Renders a cape part at the given size.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     *
     * @param cape the cape to render
     * @param typeName the cape part type (e.g. FRONT)
     * @param size the output size (height; width derived from cape aspect)
     * @return the cached cape part (PNG bytes)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public byte[] renderCape(Cape<?> cape, String typeName, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Cape rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid cape part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        Enum<?> part = cape.fromPartName(typeName);
        if (part == null || !((Cape) cape).supportsPart(part)) {
            throw new BadRequestException("Invalid or unsupported cape part: '%s'".formatted(typeName));
        }

        String canonicalKey = "%s-%s-%s.png".formatted(cape.getClass().getName(), cape.getTextureId(), part.name());
        log.debug("Getting cape part for cape: {} (part {}, size {})", cape.getTextureId(), typeName, size);

        StorageService.Bucket bucket = switch (cape) {
            case VanillaCape _ -> StorageService.Bucket.RENDERED_VANILLA_CAPES;
            case OptifineCape _ -> StorageService.Bucket.RENDERED_OPTIFINE_CAPES;
            default -> throw new IllegalStateException("Unknown cape type: " + cape.getClass().getName());
        };

        long cacheStart = System.currentTimeMillis();
        byte[] canonicalBytes = cacheEnabled ? this.storageService.get(bucket, canonicalKey) : null;
        BufferedImage canonicalImage = null;

        if (canonicalBytes == null) {
            long renderStart = System.currentTimeMillis();
            canonicalImage = ((Cape) cape).render(part, maxPartSize, RenderOptions.DEFAULT);
            canonicalBytes = ImageUtils.imageToBytes(canonicalImage, 1);
            log.debug("Took {}ms to render cape part for cape: {}", System.currentTimeMillis() - renderStart, cape.getTextureId());
            if (cacheEnabled) {
                final byte[] toUpload = canonicalBytes;
                CompletableFuture.runAsync(() -> this.storageService.upload(bucket, canonicalKey, MediaType.IMAGE_PNG_VALUE, toUpload), Main.EXECUTOR)
                    .exceptionally(ex -> {
                        log.warn("Save failed for cape part {}: {}", canonicalKey, ex.getMessage());
                        return null;
                    });
            }
        } else {
            log.debug("Got cape part for cape {} from cache in {}ms", cape.getTextureId(), System.currentTimeMillis() - cacheStart);
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = canonicalImage != null ? canonicalImage : ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }

    /**
     * Converts a {@link CapeDocument} to a {@link VanillaCape}.
     *
     * @param document the document to convert
     * @return the converted cape
     */
    public VanillaCape fromDocument(CapeDocument document) {
        if (document == null) {
            return null;
        }
        return new VanillaCape(document.getId(), document.getName(),
                document.getAccountsOwned(), document.getTextureId());
    }

    /**
     * Returns an estimated count of tracked capes for fast statistics.
     */
    public long getTrackedCapeCount() {
        return this.mongoTemplate.estimatedCount(CapeDocument.class);
    }
}
