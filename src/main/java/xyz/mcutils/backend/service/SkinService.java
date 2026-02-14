package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.dto.response.skin.SkinDTO;
import xyz.mcutils.backend.model.dto.response.skin.SkinsPageDTO;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.SkinRepository;

import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

    private final SkinRepository skinRepository;
    private final PlayerService playerService;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;

    private final Cache<String, byte[]> skinTextureCache = CacheBuilder.newBuilder()
            .expireAfterAccess(6, TimeUnit.HOURS)
            .maximumSize(1000)
            .build();
    private final Cache<String, byte[]> renderedSkinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(6, TimeUnit.HOURS)
            .maximumSize(5000)
            .build();

    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);
    private final CoalescingLoader<String, Skin> skinByTextureIdLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public SkinService(SkinRepository skinRepository, @Lazy PlayerService playerService,
                       MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.skinRepository = skinRepository;
        this.playerService = playerService;
        this.mongoTemplate = mongoTemplate;
        this.webRequest = webRequest;
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
    public Pagination.Page<SkinsPageDTO> getPaginatedSkins(int page) {
        Pagination<SkinsPageDTO> pagination = new Pagination<SkinsPageDTO>()
                .setItemsPerPage(SKINS_PER_PAGE)
                .setTotalItems(this.getTrackedSkinCount());
        return pagination.getPage(page, (pageCallback) -> {
            Query q = new Query()
                    .with(PageRequest.of(page - 1, pageCallback.getLimit()))
                    .with(Sort.by(Sort.Order.desc("accountsUsed"), Sort.Order.asc("_id")));
            return MongoUtils.findWithFields(mongoTemplate, q, SkinDocument.class, "_id", "textureId", "accountsUsed").stream()
                    .map(doc -> new SkinsPageDTO(
                            doc.get("_id", UUID.class),
                            "%s/skins/%s/fullbody_iso_front.png".formatted(
                                    AppConfig.INSTANCE.getWebPublicUrl(),
                                    doc.getString("textureId")
                            ),
                            doc.get("accountsUsed", Number.class) != null ? doc.get("accountsUsed", Number.class).longValue() : 0L
                    ))
                    .toList();
        });
    }

    /**
     * Gets the DTO for the given skin.
     *
     * @param id the UUID of the skin
     * @return the skin DTO
     */
    public SkinDTO getSkinDto(UUID id) {
        SkinDocument skinDocument = this.skinRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Skin with id '%s' not found".formatted(id)));
        String firstSeenUsing = "Unknown";
        if (skinDocument.getFirstPlayerSeenUsing() != null) {
            Query firstQuery = Query.query(Criteria.where("_id").is(skinDocument.getFirstPlayerSeenUsing())).limit(1);
            List<org.bson.Document> firstDoc = MongoUtils.findWithFields(mongoTemplate, firstQuery, PlayerDocument.class, "_id", "username");
            if (!firstDoc.isEmpty()) {
                firstSeenUsing = firstDoc.getFirst().getString("username");
            }
        }

        Query query = Query.query(Criteria.where("skin").is(skinDocument.getId()))
                .with(PageRequest.of(0, 500))
                .withHint("skin");
        List<String> accountsSeenUsing = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id", "username").stream()
                .map(doc -> doc.getString("username"))
                .toList();

        return new SkinDTO(
                skinDocument.getId(),
                skinDocument.getTextureId(),
                "%s/skins/%s/fullbody_iso_front.png".formatted(
                        AppConfig.INSTANCE.getWebPublicUrl(),
                        skinDocument.getTextureId()
                ),
                skinDocument.getAccountsUsed(),
                firstSeenUsing,
                accountsSeenUsing
        );
    }

    /**
     * Gets a skin from the database using its texture id.
     *
     * @param textureId the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByTextureId(String textureId) {
        return this.skinRepository.findByTextureId(textureId)
                .map(this::fromDocument)
                .orElse(null);
    }

    /**
     * Gets or creates the skin using the its {@link SkinTextureToken}.
     * Concurrent lookups for the same textureId share a single load (coalesced).
     *
     * @param token the texture token for the skin
     * @param playerUuid the player's uuid who was seen wearing this skin
     * @return the skin
     */
    public Skin getOrCreateSkinByTextureId(SkinTextureToken token, UUID playerUuid) {
        return skinByTextureIdLoader.get(token.getTextureId(), () -> {
            Skin skin = this.getSkinByTextureId(token.getTextureId());
            if (skin == null) {
                return this.createSkin(token, playerUuid);
            }
            return skin;
        });
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
     * @param playerUuid the player's uuid who was seen wearing this skin
     * @return the created skin
     */
    public Skin createSkin(SkinTextureToken token, UUID playerUuid) {
        long start = System.currentTimeMillis();
        SkinTextureToken.Metadata metadata = token.getMetadata();
        SkinDocument document = this.skinRepository.insert(new SkinDocument(
                UUID.randomUUID(),
                token.getTextureId(),
                EnumUtils.getEnumConstant(Skin.Model.class, metadata == null ? "DEFAULT" : metadata.getModel()),
                Skin.isLegacySkin(Skin.CDN_URL.formatted(token.getTextureId()), webRequest),
                0,
                playerUuid,
                new Date()
        ));
        log.debug("Created skin {} in {}ms", document.getTextureId(), System.currentTimeMillis() - start);
        return fromDocument(document);
    }

    /**
     * Increments {@link SkinDocument#getAccountsUsed()} by 1 for the given skin.
     *
     * @param skinId the skin document id
     */
    public void incrementAccountsUsed(UUID skinId) {
        Query query = Query.query(Criteria.where("_id").is(skinId));
        Update update = new Update().inc("accountsUsed", 1);
        mongoTemplate.updateFirst(query, update, SkinDocument.class);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param textureId the texture id of the skin to get
     * @param textureUrl the texture url of the skin to get
     * @param upgrade whether to upgrade legacy 64×32 skins to 64×64
     * @return the skin image
     */
    public byte[] getSkinTexture(String textureId, String textureUrl, boolean upgrade) {
        return textureLoader.get(textureId + "-" + upgrade, () -> {
            byte[] skinBytes;
            try {
                long start = System.currentTimeMillis();
                skinBytes = this.skinTextureCache.get(textureId + ".png", () -> {
                    log.debug("Downloading skin image for skin {}", textureId);
                    byte[] bytes = webRequest.getAsByteArray(textureUrl);
                    if (bytes == null) {
                        throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
                    }
                    log.debug("Downloaded skin image for skin {} in {}ms", textureId,  System.currentTimeMillis() - start);
                    return bytes;
                });
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            return upgrade ? SkinUtils.upgradeLegacySkin(textureId, skinBytes) : skinBytes;
        });
    }

    /**
     * Renders a skin type from the player's skin.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     *
     * @param skin the player to get the skin for
     * @param typeName the name of the type
     * @param renderOverlay whether to render the overlay
     * @param size the output size (height; width derived per type)
     * @return the skin part
     */
    public byte[] renderSkin(Skin skin, String typeName, boolean renderOverlay, int size) {
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

        String canonicalKey = "%s-%s-%s.png".formatted(skin.getTextureId(), part.name(), renderOverlay);
        byte[] canonicalBytes = cacheEnabled ? this.renderedSkinCache.getIfPresent(canonicalKey) : null;

        if (canonicalBytes == null) {
            BufferedImage img = skin.render(part, maxPartSize, new RenderOptions(renderOverlay));
            byte[] bytes = ImageUtils.imageToBytes(img, 1);
            if (cacheEnabled) {
                this.renderedSkinCache.put(canonicalKey, bytes);
            }
            canonicalBytes = bytes;
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }

    /**
     * Converts a {@link SkinDocument} to a {@link Skin}.
     *
     * @param document the document to convert
     * @return the converted skin
     */
    public Skin fromDocument(SkinDocument document) {
        if (document == null) {
            return null;
        }
        return new Skin(document.getId(), document.getTextureId(), document.getModel(), document.isLegacy());
    }

    /**
     * Returns an estimated count of tracked skins for fast statistics.
     */
    public long getTrackedSkinCount() {
        return this.mongoTemplate.estimatedCount(SkinDocument.class);
    }
}
