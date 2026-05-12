package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
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
import xyz.mcutils.backend.skin.SkinManager;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkinService {
    public static final int SKINS_PER_PAGE = 25;
    public static SkinService INSTANCE;
    private final SkinManager skinManager;
    private final PlayerService playerService;
    private final StorageService storageService;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;
    private final Cache<String, byte[]> renderedSkinCache = CacheBuilder.newBuilder().expireAfterAccess(6, TimeUnit.HOURS).maximumSize(2000).build();
    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);
    @Value("${mc-utils.renderer.skin.cache}")
    private boolean cacheEnabled;
    @Value("${mc-utils.renderer.skin.enabled}")
    private boolean renderingEnabled;
    @Value("${mc-utils.renderer.skin.limits.min_size}")
    private int minPartSize;
    @Value("${mc-utils.renderer.skin.limits.max_size}")
    private int maxPartSize;

    public SkinService(SkinManager skinManager, @Lazy PlayerService playerService, StorageService storageService, MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.skinManager = skinManager;
        this.playerService = playerService;
        this.storageService = storageService;
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
        Pagination<SkinsPageDTO> pagination = new Pagination<SkinsPageDTO>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.getTrackedSkinCount());
        return pagination.getPage(page, (pageCallback) -> {
            Query q = new Query().with(PageRequest.of(page - 1, pageCallback.limit())).with(Sort.by(Sort.Order.desc("accountsUsed"), Sort.Order.asc("_id")));
            List<Document> idDocs = MongoUtils.findWithFields(mongoTemplate, q, SkinDocument.class, "_id");
            List<UUID> ids = idDocs.stream().map(doc -> doc.get("_id", UUID.class)).toList();
            Map<UUID, SkinDocument> byId = skinManager.getByIds(ids);
            return ids.stream().map(byId::get).filter(Objects::nonNull).map(sd -> new SkinsPageDTO(sd.getId(), "%s/skins/%s/fullbody_iso_front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), sd.getTextureId()), sd.getAccountsUsed())).toList();
        });
    }

    /**
     * Gets the DTO for the given skin.
     *
     * @param id the UUID of the skin
     * @return the skin DTO
     */
    public SkinDTO getSkinDto(UUID id) {
        SkinDocument skinDocument = this.skinManager.getById(id).orElseThrow(() -> new NotFoundException("Skin with id '%s' not found".formatted(id)));
        String firstSeenUsing = "Unknown";
        if (skinDocument.getFirstPlayerSeenUsing() != null) {
            Query firstQuery = Query.query(Criteria.where("_id").is(skinDocument.getFirstPlayerSeenUsing())).limit(1);
            List<Document> firstDoc = MongoUtils.findWithFields(mongoTemplate, firstQuery, PlayerDocument.class, "_id", "username");
            if (!firstDoc.isEmpty()) {
                firstSeenUsing = firstDoc.getFirst().getString("username");
            }
        }

        Query query = Query.query(Criteria.where("skin").is(skinDocument.getId())).with(PageRequest.of(0, 500)).withHint("skin");
        List<String> accountsSeenUsing = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id", "username").stream().map(doc -> doc.getString("username")).toList();

        return new SkinDTO(skinDocument.getId(), skinDocument.getTextureId(), "%s/skins/%s/fullbody_iso_front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), skinDocument.getTextureId()), skinDocument.getAccountsUsed(), firstSeenUsing, accountsSeenUsing);
    }

    /**
     * Gets a skin using its texture id (cache or repository).
     *
     * @param textureId the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByTextureId(String textureId) {
        return this.skinManager.getByTextureId(textureId).map(this::fromDocument).orElse(null);
    }

    /**
     * Gets a skin by its id (cache or repository).
     *
     * @param id the skin document id
     * @return the skin, or null if not found
     */
    public Skin getSkinById(UUID id) {
        if (id == null) {
            return null;
        }
        return this.skinManager.getById(id).map(this::fromDocument).orElse(null);
    }

    /**
     * Gets or creates the skin using its {@link SkinTextureToken}.
     *
     * @param token      the texture token for the skin
     * @param playerUuid the player's uuid who was seen wearing this skin
     * @return the skin
     */
    public Skin getOrCreateSkinByTextureId(SkinTextureToken token, UUID playerUuid) {
        return fromDocument(skinManager.getOrCreateByTextureId(token, playerUuid));
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
        if (query.length() > 16 && query.length() != 32 && query.length() != 36) {
            skin = this.getSkinByTextureId(query);
        }
        else {
            Player player = this.playerService.getPlayer(query);
            skin = player.getSkin();
        }

        if (skin == null) {
            throw new NotFoundException("Skin for query '%s' not found".formatted(query));
        }

        return skin;
    }

    /**
     * Creates a new skin (or returns existing) and inserts into the database if needed.
     *
     * @param token      the token for the skin from the {@link MojangProfileToken}
     * @param playerUuid the player's uuid who was seen wearing this skin
     * @return the created or existing skin
     */
    public Skin createSkin(SkinTextureToken token, UUID playerUuid) {
        return fromDocument(skinManager.getOrCreateByTextureId(token, playerUuid));
    }

    /**
     * Increments accountsUsed in memory for the given skin.
     *
     * @param skinId the skin document id
     */
    public void incrementAccountsUsed(UUID skinId) {
        skinManager.incrementAccountsUsed(skinId, 1);
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param textureId  the texture id of the skin to get
     * @param textureUrl the texture url of the skin to get
     * @param upgrade    whether to upgrade legacy 64×32 skins to 64×64
     * @return the skin image
     */
    public byte[] getSkinTexture(String textureId, String textureUrl, boolean upgrade) {
        byte[] skin = textureLoader.get(textureId, () -> {
            long start = System.currentTimeMillis();

            byte[] skinBytes = this.storageService.get(StorageService.Bucket.SKINS, textureId + ".png");
            if (skinBytes == null) {
                log.debug("Downloading skin image for skin {}", textureId);
                byte[] bytes = webRequest.getAsByteArray(textureUrl);
                if (bytes == null) {
                    throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
                }
                log.debug("Downloaded skin image for skin {} in {}ms", textureId, System.currentTimeMillis() - start);
                this.storageService.upload(StorageService.Bucket.SKINS, textureId + ".png", MediaType.IMAGE_PNG_VALUE, bytes);
                skinBytes = bytes;
            }
            return skinBytes;
        });
        return SkinUtils.fixTransparentSkin(upgrade ? SkinUtils.upgradeLegacySkin(textureId, skin) : skin);
    }

    /**
     * Renders a skin type from the player's skin.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     *
     * @param skin          the player to get the skin for
     * @param typeName      the name of the type
     * @param renderOverlay whether to render the overlay
     * @param size          the output size (height; width derived per type)
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
