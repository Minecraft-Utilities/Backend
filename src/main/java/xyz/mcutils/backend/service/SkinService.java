package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.FullBodyIsoRendererBase;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.FullBodyIsoRendererBase.Side;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.skin.SkinRenderMetric;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
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
    private final StatisticsService statisticsService;
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

    public SkinService(SkinRepository skinRepository, SkinChangeEventRepository skinChangeEventRepository, PlayerRepository playerRepository,
                       @Lazy PlayerService playerService, StorageService storageService, WebRequest webRequest,  StatisticsService statisticsService) {
        this.skinRepository = skinRepository;
        this.skinChangeEventRepository = skinChangeEventRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.storageService = storageService;
        this.webRequest = webRequest;
        this.statisticsService = statisticsService;
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
            BasicPlayer player = this.playerService.getPlayer(query);
            Skin skin = player.getSkin();
            return new SkinRow(skin.getTextureId(), skin.getModel(), skin.isLegacy(), skin.getUniqueOwners(), skin.getFirstSeen());
        }
        // By texture ID
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(query);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    @Cacheable(value = "skinByTextureId", key = "#token.textureId")
    @Transactional
    public SkinRow getOrCreateSkinCached(SkinTextureToken token) {
        return getOrCreateSkin(token);
    }

    @Transactional
    public SkinRow getOrCreateSkin(SkinTextureToken token) {
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(token.getTextureId());
        if (optionalSkinRow.isPresent()) {
            return optionalSkinRow.get();
        }
        Skin.Model model = token.metadata() == null
                ? Skin.Model.DEFAULT
                : Skin.Model.valueOf(token.metadata().model().toUpperCase());
        boolean legacy = Skin.isLegacySkin(Skin.CDN_URL.formatted(token.getTextureId()), this.webRequest);
        int inserted = this.skinRepository.insertIfAbsent(token.getTextureId(), model.name(), legacy, Instant.now());
        if (inserted > 0) {
            StatisticsService.addTrackedSkinCount(1);
        }
        return this.skinRepository.findByTextureId(token.getTextureId()).orElseThrow();
    }

    public Pagination.Page<Skin> getPaginatedSkins(int page) {
        Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.statisticsService.getTrackedSkinCount());
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

    /**
     * Renders a full-body isometric skin part with a cape overlaid.
     * Only {@code FULLBODY_ISO_FRONT} and {@code FULLBODY_ISO_BACK} parts support cape rendering.
     *
     * @param skin          the skin to render
     * @param typeName      the name of the part (must be a full-body iso part)
     * @param renderOverlay whether to render the skin overlay layers
     * @param size          the output height in pixels
     * @param cape          the cape to render alongside the skin
     * @return the rendered image as PNG bytes
     */
    public byte[] renderSkinWithCape(Skin skin, String typeName, boolean renderOverlay, int size, VanillaCape cape) {
        if (!renderingEnabled) {
            throw new BadRequestException("Skin rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid skin part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        Skin.SkinPart part = EnumUtils.getEnumConstant(Skin.SkinPart.class, typeName);
        if (part != Skin.SkinPart.FULLBODY_ISO_FRONT && part != Skin.SkinPart.FULLBODY_ISO_BACK) {
            throw new BadRequestException("Cape rendering is only supported for full-body isometric parts");
        }

        String canonicalKey = "%s-%s-%s-%s.png".formatted(skin.getTextureId(), part.name(), renderOverlay, cape.getTextureId());
        byte[] canonicalBytes = cacheEnabled ? this.renderedSkinCache.getIfPresent(canonicalKey) : null;

        if (canonicalBytes == null) {
            long renderStart = System.currentTimeMillis();
            FullBodyIsoRendererBase.Side side = (part == Skin.SkinPart.FULLBODY_ISO_BACK) ? FullBodyIsoRendererBase.Side.BACK : FullBodyIsoRendererBase.Side.FRONT;
            BufferedImage img = FullBodyIsoRendererBase.INSTANCE.render(skin, cape, side, renderOverlay, maxPartSize);
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

        BufferedImage capeImage = ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(capeImage, size), 1);
    }
}
