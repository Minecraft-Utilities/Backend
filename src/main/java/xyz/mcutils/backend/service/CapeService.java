package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.model.domain.cape.CapeType;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayerCapePart;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.repository.redis.PlayerCapePartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    private final StorageService minioService;
    private final PlayerService playerService;
    private final PlayerCapePartCacheRepository capePartRepository;
    private final CapeRepository capeRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public CapeService(StorageService minioService, @Lazy PlayerService playerService, PlayerCapePartCacheRepository capePartRepository, CapeRepository capeRepository, MongoTemplate mongoTemplate) {
        this.minioService = minioService;
        this.playerService = playerService;
        this.capePartRepository = capePartRepository;
        this.capeRepository = capeRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets all the known capes sorted by accounts owned.
     *
     * @return the known capes
     */
    public Map<String, VanillaCape> getCapes() {
        Map<String, VanillaCape>  capes = new LinkedHashMap<>();
        for (CapeDocument document : this.capeRepository.findAllByOrderByAccountsOwnedDescIdAsc()) {
            capes.put(document.getTextureId(), new VanillaCape(
                    document.getId(),
                    document.getName(),
                    document.getAccountsOwned(),
                    document.getTextureId()
            ));
        }
        return capes;
    }

    /**
     * Gets a cape from the database using its texture id.
     *
     * @param textureId the cape to get
     * @return the cape, or null if not found
     */
    public VanillaCape getCapeByTextureId(String textureId) {
        long start = System.currentTimeMillis();
        Optional<CapeDocument> optionalCapeDocument = this.capeRepository.findByTextureId(textureId);
        CapeDocument document;

        if (optionalCapeDocument.isPresent()) {
            document = optionalCapeDocument.get();
        } else {
            // Check to see if the cape texture is valid.
            boolean exists = false;
            try {
                exists = VanillaCape.capeExists(textureId).get();
            } catch (Exception ignored) { }
            if (!exists) {
                throw new NotFoundException("Cape with texture id " + textureId + " was not found");
            }

            document = this.capeRepository.insert(new CapeDocument(
                    UUID.randomUUID(),
                    null,
                    textureId,
                    0,
                    new Date()
            ));
        }

        log.debug("Found vanilla cape by texture id {} in {}ms", document.getId(), System.currentTimeMillis() - start);
        return new VanillaCape(
                document.getId(),
                document.getName(),
                document.getAccountsOwned(),
                document.getTextureId()
        );
    }

    /**
     * Gets a cape from the database using its UUID.
     *
     * @param uuid the uuid of the cape
     * @return the cape, or null if not found
     */
    public VanillaCape getCapeByUuid(UUID uuid) {
        long start = System.currentTimeMillis();
        Optional<CapeDocument> optionalCapeDocument = this.capeRepository.findById(uuid);
        if (optionalCapeDocument.isPresent()) {
            CapeDocument document = optionalCapeDocument.get();
            log.debug("Found vanilla cape by uuid {} in {}ms", document.getId(), System.currentTimeMillis() - start);
            return new VanillaCape(
                    document.getId(),
                    document.getName(),
                    document.getAccountsOwned(),
                    document.getTextureId()
            );
        }
        return null;
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
     * Increments {@link CapeDocument#getAccountsOwned()} by 1 for the given cape.
     *
     * @param capeId the cape document id
     */
    public void incrementAccountsOwned(UUID capeId) {
        Query query = Query.query(Criteria.where("_id").is(capeId));
        Update update = new Update().inc("accountsOwned", 1);
        mongoTemplate.updateFirst(query, update, CapeDocument.class);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param cape the skin to get the image for
     * @return the skin image
     */
    public byte[] getCapeTexture(Cape<?> cape) {
        StorageService.Bucket bucket = switch (cape) {
            case VanillaCape _ -> StorageService.Bucket.VANILLA_CAPES;
            case OptifineCape _ -> StorageService.Bucket.OPTIFINE_CAPES;
            default -> null;
        };

        byte[] capeBytes = minioService.get(bucket, cape.getTextureId() + ".png");
        if (capeBytes == null) {
            log.debug("Downloading skin image for skin {}", cape.getTextureId());
            capeBytes = PlayerUtils.getImage(cape.getRawTextureUrl());
            if (capeBytes == null) {
                throw new IllegalStateException("Cape with id '%s' was not found".formatted(cape.getTextureId()));
            }
            minioService.upload(bucket, cape.getTextureId() + ".png", MediaType.IMAGE_PNG_VALUE, capeBytes);
            log.debug("Saved cape image for skin {}", cape.getTextureId());
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
     * @param typeName the cape part type (e.g. FRONT)
     * @param size the output size (height; width derived from cape aspect)
     * @return the cached cape part (PNG bytes)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CachedPlayerCapePart renderCape(Cape<?> cape, String typeName, int size) {
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

        String key = "%s-%s-%s-%s".formatted(cape.getClass().getName(), cape.getTextureId(), part.name(), size);

        log.debug("Getting cape part for cape: {} (part {}, size {})", cape.getTextureId(), typeName, size);

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerCapePart> cache = capePartRepository.findById(key);
            if (cache.isPresent()) {
                log.debug("Got cape part for cape {} from cache in {}ms", cape.getTextureId(), System.currentTimeMillis() - cacheStart);
                return cache.get();
            }
        }

        long renderStart = System.currentTimeMillis();
        BufferedImage renderedPart = ((Cape) cape).render(part, size, RenderOptions.EMPTY);
        byte[] pngBytes = ImageUtils.imageToBytes(renderedPart);
        log.debug("Took {}ms to render cape part for cape: {}", System.currentTimeMillis() - renderStart, cape.getTextureId());

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
