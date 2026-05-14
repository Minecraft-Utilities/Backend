package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.skin.SkinRenderMetric;
import xyz.mcutils.backend.model.domain.player.BasicPlayer;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinChangeEventRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinChangeEventRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkinService {
    public static final int SKINS_PER_PAGE = 25;
    public static SkinService INSTANCE;

    private final SkinRepository skinRepository;
    private final SkinChangeEventRepository skinChangeEventRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final StorageService storageService;
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

    public SkinService(SkinRepository skinRepository, SkinChangeEventRepository skinChangeEventRepository, PlayerRepository playerRepository, @Lazy PlayerService playerService, StorageService storageService, WebRequest webRequest) {
        this.skinRepository = skinRepository;
        this.skinChangeEventRepository = skinChangeEventRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.storageService = storageService;
        this.webRequest = webRequest;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public Skin getSkinById(long id) {
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findById(id);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        SkinRow skinRow = optionalSkinRow.get();
        Skin skin = Skin.fromRow(skinRow);

        Optional<SkinChangeEventRow> firstEvent = this.skinChangeEventRepository.findFirstBySkinId(skinRow.getId());
        if (firstEvent.isPresent()) {
            Optional<PlayerRow> firstPlayer = this.playerRepository.findById(firstEvent.get().getPlayerId());
            firstPlayer.ifPresent(p -> skin.setFirstSeenUsing(p.getUsername()));
        }

        List<PlayerRow> usersUsing = this.playerRepository.findBySkinId(skinRow.getId(), PageRequest.of(0, 100));
        skin.setAccountsSeenUsing(usersUsing.stream().map(PlayerRow::getUsername).toList());

        return skin;
    }

    public SkinRow getSkinByTextureIdOrPlayer(String query) {
        // By player
        if (query.length() <= 36) {
            BasicPlayer player = this.playerService.getPlayer(query);
            Skin skin = player.getSkin();
            return new SkinRow(skin.getTextureId(), skin.getModel(), skin.isLegacy(), skin.getUniqueOwners(), skin.getFirstSeen());
        }
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(query);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    @Transactional
    public SkinRow getOrCreateSkin(SkinTextureToken token) {
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(token.getTextureId());
        if (optionalSkinRow.isPresent()) {
            return optionalSkinRow.get();
        }
        SkinRow skinRow = this.skinRepository.save(new SkinRow(
                token.getTextureId(),
                token.metadata() == null ? Skin.Model.DEFAULT : Skin.Model.valueOf(token.metadata().model().toUpperCase()),
                Skin.isLegacySkin(Skin.CDN_URL.formatted(token.getTextureId()), this.webRequest),
                0,
                Instant.now()
        ));
        StatisticsService.addTrackedSkinCount(1);
        return skinRow;
    }

    public Pagination.Page<Skin> getPaginatedSkins(int page) {
        Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.skinRepository.count());
        return pagination.getPage(page, (pageCallback) -> {
            Pageable pageable = PageRequest.of(
                    page - 1,
                    pageCallback.limit()
            );
            return this.skinRepository.findAllOrderByUniqueOwnersDescIdAsc(pageable).map(Skin::fromRow).stream().toList();
        });
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
            long renderStart = System.currentTimeMillis();
            BufferedImage img = skin.render(part, maxPartSize, new RenderOptions(renderOverlay));
            byte[] bytes = ImageUtils.imageToBytes(img, 1);
            if (cacheEnabled) {
                this.renderedSkinCache.put(canonicalKey, bytes);
            }
            MetricService.getMetric(SkinRenderMetric.class).recordMiss(System.currentTimeMillis() - renderStart);
            canonicalBytes = bytes;
        } else {
            MetricService.getMetric(SkinRenderMetric.class).recordHit();
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }

//    /**
//     * Gets a paginated list of skins.
//     *
//     * @param page the page to get
//     * @return the paginated list of skins
//     */
//    public Pagination.Page<Skin> getPaginatedSkins(int page) {
//        Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.getTrackedSkinCount());
//        return pagination.getPage(page, (pageCallback) -> {
//            Query q = new Query().with(PageRequest.of(page - 1, pageCallback.limit())).with(Sort.by(Sort.Order.desc("accountsUsed"), Sort.Order.asc("_id")));
//            List<Document> idDocs = MongoUtils.findWithFields(mongoTemplate, q, SkinDocument.class, "_id");
//            List<UUID> ids = idDocs.stream().map(doc -> doc.get("_id", UUID.class)).toList();
//            Map<UUID, SkinDocument> byId = skinManager.getByIds(ids);
//            return ids.stream().map(byId::get).filter(Objects::nonNull).map(this::fromDocument).toList();
//        });
//    }
//
//    /**
//     * Gets the DTO for the given skin.
//     *
//     * @param id the UUID of the skin
//     * @return the skin DTO
//     */
//    public Skin getSkin(UUID id) {
//        SkinDocument skinDocument = this.skinManager.getById(id).orElseThrow(() -> new NotFoundException("Skin with id '%s' not found".formatted(id)));
//        String firstSeenUsing = null;
//        if (skinDocument.getFirstPlayerSeenUsing() != null) {
//            Query firstQuery = Query.query(Criteria.where("_id").is(skinDocument.getFirstPlayerSeenUsing())).limit(1);
//            List<Document> firstDoc = MongoUtils.findWithFields(mongoTemplate, firstQuery, PlayerDocument.class, "_id", "username");
//            if (!firstDoc.isEmpty()) {
//                firstSeenUsing = firstDoc.getFirst().getString("username");
//            }
//        }
//
//        Query query = Query.query(Criteria.where("skin").is(skinDocument.getId())).with(PageRequest.of(0, 500)).withHint("skin");
//        List<String> accountsSeenUsing = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id", "username").stream().map(doc -> doc.getString("username")).toList();
//
//        Skin skin = fromDocument(skinDocument);
//        skin.setFirstSeenUsing(firstSeenUsing);
//        skin.setAccountsSeenUsing(accountsSeenUsing);
//        return skin;
//    }
//
//    /**
//     * Gets a skin using its texture id (cache or repository).
//     *
//     * @param textureId the skin to get
//     * @return the skin, or null if not found
//     */
//    public Skin getSkinByTextureId(String textureId) {
//        return this.skinManager.getByTextureId(textureId).map(this::fromDocument).orElse(null);
//    }
//
//    /**
//     * Gets a skin by its id (cache or repository).
//     *
//     * @param id the skin document id
//     * @return the skin, or null if not found
//     */
//    public Skin getSkinById(UUID id) {
//        if (id == null) {
//            return null;
//        }
//        return this.skinManager.getById(id).map(this::fromDocument).orElse(null);
//    }
//
//    /**
//     * Gets or creates the skin using its {@link SkinTextureToken}.
//     *
//     * @param token      the texture token for the skin
//     * @param playerUuid the player's uuid who was seen wearing this skin
//     * @return the skin
//     */
//    public Skin getOrCreateSkinByTextureId(SkinTextureToken token, UUID playerUuid) {
//        return fromDocument(skinManager.getOrCreateByTextureId(token, playerUuid));
//    }
//
//    /**
//     * Gets a Skin from the texture id or the player's name / uuid.
//     *
//     * @param query the query to search for
//     * @return the skin, or null
//     */
//    public Skin getSkinFromTextureIdOrPlayer(String query) {
//        Skin skin;
//        // I really have no idea how long their sha-1 string length is
//        // a player name can't be more than 16 chars, so just assume it's a texture id
//        if (query.length() > 16 && query.length() != 32 && query.length() != 36) {
//            skin = this.getSkinByTextureId(query);
//        }
//        else {
//            Player player = this.playerService.getPlayer(query);
//            skin = player.getSkin();
//        }
//
//        if (skin == null) {
//            throw new NotFoundException("Skin for query '%s' not found".formatted(query));
//        }
//
//        return skin;
//    }
//
//    /**
//     * Creates a new skin (or returns existing) and inserts into the database if needed.
//     *
//     * @param token      the token for the skin from the {@link MojangProfileToken}
//     * @param playerUuid the player's uuid who was seen wearing this skin
//     * @return the created or existing skin
//     */
//    public Skin createSkin(SkinTextureToken token, UUID playerUuid) {
//        return fromDocument(skinManager.getOrCreateByTextureId(token, playerUuid));
//    }
//
//    /**
//     * Increments accountsUsed in memory for the given skin.
//     *
//     * @param skinId the skin document id
//     */
//    public void incrementAccountsUsed(UUID skinId) {
//        skinManager.incrementAccountsUsed(skinId, 1);
//    }
//
//
//    /**
//     * Converts a {@link SkinDocument} to a {@link Skin}.
//     *
//     * @param document the document to convert
//     * @return the converted skin
//     */
//    public Skin fromDocument(SkinDocument document) {
//        if (document == null) {
//            return null;
//        }
//        Skin skin = new Skin(document.getId(), document.getTextureId(), document.getModel(), document.isLegacy());
//        skin.setAccountsUsed(document.getAccountsUsed());
//        skin.setFirstSeen(document.getFirstSeen());
//        return skin;
//    }
//
//    /**
//     * Returns an estimated count of tracked skins for fast statistics.
//     */
//    public long getTrackedSkinCount() {
//        return this.mongoTemplate.estimatedCount(SkinDocument.class);
//    }
}
