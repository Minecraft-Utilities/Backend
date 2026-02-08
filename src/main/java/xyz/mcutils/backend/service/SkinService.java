package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.SkinRepository;
import xyz.mcutils.backend.repository.redis.PlayerSkinPartCacheRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SkinService {
    public static final int SKINS_PER_PAGE = 25;
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
    private final SkinRepository skinRepository;
    private final StorageService minioService;
    private final PlayerService playerService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public SkinService(PlayerSkinPartCacheRepository skinPartRepository, SkinRepository skinRepository, StorageService minioService, @Lazy PlayerService playerService, MongoTemplate mongoTemplate) {
        this.skinPartRepository = skinPartRepository;
        this.skinRepository = skinRepository;
        this.minioService = minioService;
        this.playerService = playerService;
        this.mongoTemplate = mongoTemplate;  
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Gets a paginated list of skins.
     *
     * @param page the page to get
     * @return the paginated list of skins
     */
    public Pagination.Page<Skin> getPaginatedSkins(int page) {
        Pagination<Skin> pagination = new Pagination<Skin>()
                .setItemsPerPage(SKINS_PER_PAGE)
                .setTotalItems(this.skinRepository.count());
        return pagination.getPage(page, (pageCallback) -> this.skinRepository.findAll(PageRequest.of(page, pageCallback.getLimit())).getContent().stream()
                .map(skinDocument -> new Skin(
                        skinDocument.getId(),
                        skinDocument.getTextureId(),
                        skinDocument.getModel(),
                        skinDocument.isLegacy(),
                        skinDocument.getAccountsUsed()
                )).toList());
    }

    /**
     * Gets a skin from the database using its texture id.
     *
     * @param textureId the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByTextureId(String textureId) {
        long start = System.currentTimeMillis();
        Optional<SkinDocument> optionalSkinDocument = this.skinRepository.findByTextureId(textureId);
        if (optionalSkinDocument.isPresent()) {
            SkinDocument document = optionalSkinDocument.get();
            log.debug("Found skin by texture id {} in {}ms", document.getTextureId(), System.currentTimeMillis() - start);
            return new Skin(
                    document.getId(),
                    document.getTextureId(),
                    document.getModel(),
                    document.isLegacy(),
                    document.getAccountsUsed()
            );
        }
        return null;
    }

    /**
     * Gets a skin from the database using its UUID.
     *
     * @param uuid the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByUuid(UUID uuid) {
        long start = System.currentTimeMillis();
        Optional<SkinDocument> optionalSkinDocument = this.skinRepository.findById(uuid);
        if (optionalSkinDocument.isPresent()) {
            SkinDocument document = optionalSkinDocument.get();
            log.debug("Found skin by uuid {} in {}ms", document.getId(), System.currentTimeMillis() - start);
            return new Skin(
                    document.getId(),
                    document.getTextureId(),
                    document.getModel(),
                    document.isLegacy(),
                    document.getAccountsUsed()
            );
        }
        return null;
    }

    /**
     * Gets or creates the skin using the its {@link SkinTextureToken}
     *
     * @param token the texture token for the skin
     * @return the skin
     */
    public Skin getOrCreateSkinByTextureId(SkinTextureToken token) {
        Skin skin = this.getSkinByTextureId(token.getTextureId());
        if (skin == null) {
            return this.createSkin(token);
        }
        return skin;
    }

    /**
     * Gets a Skin from the texture id or the player's name / uuid.
     *
     * @param query the query to search for
     * @return the skin, or null
     */
    public Skin getSkinFromTextureIdOrPlayer(String query) {
        Skin skin;
        // I really have no idea how long their sha-1 string length is
        // a player name can't be more than 16 chars, so just assume it's a texture id
        if (query.length() > 16) {
            skin = this.getSkinByTextureId(query);
        } else {
            Player player = this.playerService.getPlayer(query);
            skin = player.getSkin();
        }

        if (skin == null) {
            throw new NotFoundException("Skin for query '%s' not found".formatted(query));
        }

        return skin;
    }

    /**
     * Creates a new skin and inserts it into the database.
     *
     * @param token the token for the skin from the {@link MojangProfileToken}
     * @return the created skin
     */
    public Skin createSkin(SkinTextureToken token) {
        long start = System.currentTimeMillis();
        SkinTextureToken.Metadata metadata = token.getMetadata();
        SkinDocument document = this.skinRepository.insert(new SkinDocument(
                UUID.randomUUID(),
                token.getTextureId(),
                EnumUtils.getEnumConstant(Skin.Model.class, metadata == null ? "DEFAULT" : metadata.getModel()),
                Skin.isLegacySkin(token.getTextureId(), Skin.CDN_URL.formatted(token.getTextureId())),
                0,
                new Date()
        ));
        log.debug("Created skin {} in {}ms", document.getTextureId(), System.currentTimeMillis() - start);
        return new Skin(
                document.getId(),
                document.getTextureId(),
                document.getModel(),
                document.isLegacy(),
                document.getAccountsUsed()
        );
    }

    /**
     * Increments {@link SkinDocument#getAccountsUsed()} by 1 for the given skin.
     *
     * @param capeId the skin document id
     */
    public void incrementAccountsUsed(UUID capeId) {
        Query query = Query.query(Criteria.where("_id").is(capeId));
        Update update = new Update().inc("accountsUsed", 1);
        mongoTemplate.updateFirst(query, update, SkinDocument.class);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param textureId the texture id of the skin to get
     * @param textureUrl the texture url of the skin to get
     * @return the skin image
     */
    public byte[] getSkinTexture(String textureId, String textureUrl, boolean upgrade) {
        byte[] skinBytes = minioService.get(StorageService.Bucket.SKINS, textureId + ".png");
        if (skinBytes == null) {
            log.debug("Downloading skin image for skin {}", textureId);
            skinBytes = PlayerUtils.getImage(textureUrl);
            if (skinBytes == null) {
                throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
            }
            minioService.upload(StorageService.Bucket.SKINS, textureId + ".png", MediaType.IMAGE_PNG_VALUE, skinBytes);
            log.debug("Saved skin image for skin {}", textureId);
        }
        return upgrade ? SkinUtils.upgradeLegacySkin(textureId, skinBytes) : skinBytes;
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

        Skin.SkinPart part = EnumUtils.getEnumConstant(Skin.SkinPart.class, typeName);
        if (part == null || !skin.supportsPart(part)) {
            throw new BadRequestException("Invalid or unsupported skin part: '%s'".formatted(typeName));
        }
        String name = part.name();
        String key = "%s-%s-%s-%s-%s".formatted(skin.getClass().getName(), skin.getTextureId(), name, size, renderOverlay);

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
        BufferedImage renderedPart = skin.render(part, size, RenderOptions.of(renderOverlay));
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
