package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.skin.SkinRenderMetric;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.domain.skin.SkinLookupSort;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkinService {
    public static final String DEFAULT_SKIN = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb"; // Steve
    public static final int SKINS_PER_PAGE = 25;
    public static SkinService INSTANCE;

    private final SkinRepository skinRepository;
    private final PlayerRepository playerRepository;
    private final StorageService storageService;
    private final StatisticsService statisticsService;
    private final WebRequest webRequest;
    private final Cache<String, byte[]> renderedSkinCache = CacheBuilder.newBuilder().expireAfterAccess(6, TimeUnit.HOURS).maximumSize(2000).build();
    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);
    private final CoalescingLoader<String, SkinRow> skinCreationLoader = new CoalescingLoader<>(Runnable::run);
    private final TransactionTemplate transactionTemplate;

    @Value("${mc-utils.renderer.skin.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.skin.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.skin.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.skin.limits.max_size}")
    private int maxPartSize;

    public SkinService(SkinRepository skinRepository, PlayerRepository playerRepository,
                       StorageService storageService, WebRequest webRequest, StatisticsService statisticsService,
                       PlatformTransactionManager transactionManager) {
        this.skinRepository = skinRepository;
        this.playerRepository = playerRepository;
        this.storageService = storageService;
        this.webRequest = webRequest;
        this.statisticsService = statisticsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void updateTrendingHeat() {
        long before = System.currentTimeMillis();
        this.skinRepository.resetTrendingHeat();
        this.skinRepository.updateTrendingHeat();
        log.info("Updated trending heat for skins in {}ms", System.currentTimeMillis() - before);
    }

    public Skin getSkinById(long id) {
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findById(id);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        SkinRow skinRow = optionalSkinRow.get();
        Skin skin = Skin.fromRow(skinRow);

        if (skinRow.getFirstSeenUsingPlayerId() != null) {
            this.playerRepository.findById(skinRow.getFirstSeenUsingPlayerId())
                    .ifPresent(p -> skin.setFirstSeenUsing(p.getUsername()));
        }

        List<String> usersUsing = this.playerRepository.findUsernamesBySkinId(skinRow.getId(), PageRequest.of(0, 250));
        skin.setAccountsSeenUsing(usersUsing);

        return skin;
    }

    public SkinRow getSkinByQuery(String query) {
        // By numeric ID
        if (!query.isEmpty() && query.chars().allMatch(Character::isDigit)) {
            Optional<SkinRow> optionalSkinRow = this.skinRepository.findById(Long.parseLong(query));
            if (optionalSkinRow.isEmpty()) {
                throw new NotFoundException("Skin not found");
            }
            return optionalSkinRow.get();
        }
        // By player (name or UUID)
        if (query.length() <= 36) {
            return this.getPlayerSkinRow(query);
        }
        // By texture ID
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(query);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    public SkinRow getPlayerSkinRow(String playerQuery) {
        boolean isUsername = playerQuery.length() <= 16;
        Optional<SkinRow> optionalSkinRow = isUsername
                ? this.playerRepository.findSkinByUsernameIgnoreCase(playerQuery)
                : this.playerRepository.findSkinById(UUIDUtils.parseUuid(playerQuery));
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found for player '%s'".formatted(playerQuery));
        }
        return optionalSkinRow.get();
    }

    /**
     * Cached variant of {@link #getOrCreateSkin(SkinTextureToken, UUID)}.
     * Cache key is the texture ID only; {@code playerId} is only relevant on first insert.
     */
    @Cacheable(value = "skinByTextureId", key = "#token.textureId")
    public SkinRow getOrCreateSkinCached(SkinTextureToken token, UUID playerId) {
        return getOrCreateSkin(token, playerId);
    }

    public SkinRow getOrCreateSkin(SkinTextureToken token, UUID playerId) {
        String textureId = token.getTextureId();
        Optional<SkinRow> existing = this.skinRepository.findByTextureId(textureId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return this.skinCreationLoader.get(textureId, () -> this.transactionTemplate.execute(_ -> this.insertSkinIfAbsentUnderLock(token, playerId)));
    }

    private SkinRow insertSkinIfAbsentUnderLock(SkinTextureToken token, UUID playerId) {
        String textureId = token.getTextureId();
        this.skinRepository.acquireCreateLock(textureId, SkinRepository.CREATE_LOCK_CLASS);

        Optional<SkinRow> existing = this.skinRepository.findByTextureId(textureId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Skin.Model model = token.metadata() == null
                ? Skin.Model.DEFAULT
                : Skin.Model.valueOf(token.metadata().model().toUpperCase());
        boolean legacy = Skin.isLegacySkin(Skin.CDN_URL.formatted(textureId), this.webRequest);
        int inserted = this.skinRepository.insertIfAbsent(textureId, model.name(), legacy, Instant.now(), playerId);
        if (inserted > 0) {
            StatisticsService.addTrackedSkinCount(1);
        }
        return this.skinRepository.findByTextureId(textureId).orElseThrow();
    }

    public Pagination.Page<Skin> getPaginatedSkins(int page, SkinLookupSort sort) {
        Sort pageSort = Sort.by(Sort.Direction.DESC, sort.getFieldName()).and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable pageable = PageRequest.of(page - 1, SKINS_PER_PAGE, pageSort);

        if (sort == SkinLookupSort.TRENDING) {
            long total = this.skinRepository.countByTrendingHeatGreaterThan(0);
            Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(total);
            return pagination.getPage(page, (_) ->
                this.skinRepository.findTrendingSkins(pageable).map(Skin::fromRow).stream().toList()
            );
        }

        Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.statisticsService.getTrackedSkinCount());
        return pagination.getPage(page, (_) ->
            this.skinRepository.findAllSkins(pageable).map(Skin::fromRow).stream().toList()
        );
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
                byte[] bytes = webRequest.request(textureUrl).asBytes();
                if (bytes == null) {
                    bytes = webRequest.request("https://textures.minecraft.net/texture/%s".formatted(DEFAULT_SKIN)).asBytes();
                    log.info("Skin not found for the texture {}, using fallback skin", textureId);
                    if (bytes == null) {
                        throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
                    }
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
     * Renders a skin part, optionally with a cape.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     * Cape rendering is only supported for {@code FULLBODY_ISO_FRONT} and {@code FULLBODY_ISO_BACK} parts.
     *
     * @param skin     the skin to render
     * @param typeName the name of the part
     * @param options  render options (overlay flag and optional cape)
     * @param size     the output size (height; width derived per type)
     * @return the rendered image as PNG bytes
     */
    public byte[] renderSkin(Skin skin, String typeName, RenderOptions options, int size) {
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

        String canonicalKeyBase = "%s-%s-%s".formatted(skin.getTextureId(), part.name(), options.renderOverlays());
        String canonicalKey = options.cape() != null ? canonicalKeyBase + "-" + options.cape().getTextureId() : canonicalKeyBase;
        byte[] canonicalBytes = cacheEnabled ? this.renderedSkinCache.getIfPresent(canonicalKey) : null;

        if (canonicalBytes == null) {
            log.debug("Rendering skin part {} for skin {}", part.name(), skin.getTextureId());
            long renderStart = System.currentTimeMillis();
            BufferedImage img = skin.render(part, maxPartSize, options);
            byte[] bytes = ImageUtils.imageToBytes(img, 1);
            if (cacheEnabled) {
                this.renderedSkinCache.put(canonicalKey, bytes);
            }
            long elapsed = System.currentTimeMillis() - renderStart;
            log.debug("Rendered skin part {} for skin {} in {}ms", part.name(), skin.getTextureId(), elapsed);
            MetricService.getMetric(SkinRenderMetric.class).recordMiss(elapsed);
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
}
