package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinRepository;

import java.awt.image.BufferedImage;
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

    private final SkinRepository skinRepository;
    private final PlayerRepository playerRepository;
    private final StorageService storageService;
    private final PlayerService playerService;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;

    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);

    @Autowired
    public SkinService(SkinRepository skinRepository, PlayerRepository playerRepository, StorageService storageService, @Lazy PlayerService playerService,
                       MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.skinRepository = skinRepository;
        this.playerRepository = playerRepository;
        this.storageService = storageService;
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
        return pagination.getPage(page, (pageCallback) -> this.skinRepository.findListByOrderByAccountsUsedDescIdAsc(PageRequest.of(page - 1, pageCallback.getLimit()))
                .stream().map(skinDocument -> new SkinsPageDTO(
                        skinDocument.getId(),
                        "%s/skins/%s/fullbody_front.png".formatted(
                                AppConfig.INSTANCE.getWebPublicUrl(),
                                skinDocument.getTextureId()
                        ),
                        skinDocument.getAccountsUsed()
                )).toList());
    }

    /**
     * Gets the DTO for the given skin.
     *
     * @param id the UUID of the skin
     * @return the skin DTO
     */
    public SkinDTO getSkinDto(UUID id) {
        Optional<SkinDocument> optionalSkinDocument = this.skinRepository.findById(id);
        if (optionalSkinDocument.isEmpty()) {
            throw new NotFoundException("Skin with id '%s' not found'".formatted(id));
        }

        SkinDocument skinDocument = optionalSkinDocument.get();
        Player firstPlayerSeenUsing = this.playerService.getPlayer(skinDocument.getFirstPlayerSeenUsing().toString());

        return new SkinDTO(
                skinDocument.getId(),
                "%s/skins/%s/fullbody_front.png".formatted(
                        AppConfig.INSTANCE.getWebPublicUrl(),
                        skinDocument.getTextureId()
                ),
                skinDocument.getAccountsUsed(),
                firstPlayerSeenUsing.getUsername(),
                this.playerRepository.findBySkinId(
                        skinDocument.getId(),
                        Pageable.ofSize(100)
                ).stream().map(PlayerDocument::getUsername).toList()
        );
    }

    /**
     * Gets a skin from the database using its texture id.
     *
     * @param textureId the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByTextureId(String textureId) {
        Optional<SkinDocument> optionalSkinDocument = this.skinRepository.findByTextureId(textureId);
        if (optionalSkinDocument.isPresent()) {
            SkinDocument document = optionalSkinDocument.get();
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
     * @param id the skin to get
     * @return the skin, or null if not found
     */
    public Skin getSkinByUuid(UUID id) {
        long start = System.currentTimeMillis();
        Optional<SkinDocument> optionalSkinDocument = this.skinRepository.findById(id);
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
     * @param playerUuid the player's uuid who was seen wearing this skin
     * @return the skin
     */
    public Skin getOrCreateSkinByTextureId(SkinTextureToken token, UUID playerUuid) {
        Skin skin = this.getSkinByTextureId(token.getTextureId());
        if (skin == null) {
            return this.createSkin(token, playerUuid);
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
                Skin.isLegacySkin(token.getTextureId(), Skin.CDN_URL.formatted(token.getTextureId())),
                0,
                playerUuid,
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
     * @param upgrade whether to upgrade legacy 64×32 skins to 64×64
     * @return the skin image
     */
    public byte[] getSkinTexture(String textureId, String textureUrl, boolean upgrade) {
        return textureLoader.get(textureId + "-" + upgrade, () -> {
            byte[] skinBytes = storageService.get(StorageService.Bucket.SKINS, textureId + ".png");
            if (skinBytes == null) {
                log.debug("Downloading skin image for skin {}", textureId);
                skinBytes = PlayerUtils.getImage(textureUrl, webRequest);
                if (skinBytes == null) {
                    throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
                }
                storageService.upload(StorageService.Bucket.SKINS, textureId + ".png", MediaType.IMAGE_PNG_VALUE, skinBytes);
                log.debug("Saved skin image for skin {}", textureId);
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
        byte[] canonicalBytes = cacheEnabled ? this.storageService.get(StorageService.Bucket.RENDERED_SKINS, canonicalKey) : null;
        BufferedImage canonicalImage = null;

        if (canonicalBytes == null) {
            canonicalImage = skin.render(part, maxPartSize, RenderOptions.of(renderOverlay));
            canonicalBytes = ImageUtils.imageToBytes(canonicalImage, 1);
            if (cacheEnabled) {
                final byte[] toUpload = canonicalBytes;
                CompletableFuture.runAsync(() -> this.storageService.upload(StorageService.Bucket.RENDERED_SKINS, canonicalKey, MediaType.IMAGE_PNG_VALUE, toUpload), Main.EXECUTOR)
                    .exceptionally(ex -> {
                        log.warn("Save failed for skin part {}: {}", canonicalKey, ex.getMessage());
                        return null;
                    });
            }
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = canonicalImage != null ? canonicalImage : ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }

    /**
     * Converts a {@link SkinDocument} to a {@link Skin}.
     *
     * @param document the document to convert
     * @return the converted skin
     */
    public Skin fromDocument(SkinDocument document) {
        if (document == null) return null;
        return new Skin(document.getId(), document.getTextureId(), document.getModel(),
                document.isLegacy(), document.getAccountsUsed());
    }

    /**
     * Returns an estimated count of tracked skins for fast statistics.
     */
    public long getTrackedSkinCount() {
        return this.mongoTemplate.estimatedCount(SkinDocument.class);
    }
}
