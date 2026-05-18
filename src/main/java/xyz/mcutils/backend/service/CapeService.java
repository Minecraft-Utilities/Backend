package xyz.mcutils.backend.service;

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
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.cape.CapeRenderMetric;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.repository.postgres.CapeRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class CapeService {
    public static final int CAPES_PER_PAGE = 50;
    public static CapeService INSTANCE;

    private final StorageService storageService;
    private final PlayerService playerService;
    private final StatisticsService statisticsService;
    private final CapeRepository capeRepository;
    private final PlayerRepository playerRepository;
    private final WebRequest webRequest;
    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);

    @Value("${mc-utils.renderer.cape.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.cape.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.cape.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.cape.limits.max_size}")
    private int maxPartSize;

    @Value("${mc-utils.webhooks.new_cape_discovered}")
    private String newCapeDiscoveredWebhook;

    public CapeService(StorageService storageService, @Lazy PlayerService playerService, StatisticsService statisticsService,
                       CapeRepository capeRepository, PlayerRepository playerRepository, WebRequest webRequest) {
        this.storageService = storageService;
        this.playerService = playerService;
        this.statisticsService = statisticsService;
        this.capeRepository = capeRepository;
        this.playerRepository = playerRepository;
        this.webRequest = webRequest;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public VanillaCape getCapeById(long id) {
        Optional<CapeRow> optionalCapeRow = this.capeRepository.findById(id);
        if (optionalCapeRow.isEmpty()) {
            throw new NotFoundException("Cape not found");
        }
        CapeRow capeRow = optionalCapeRow.get();
        VanillaCape cape = VanillaCape.fromRow(capeRow);

        if (capeRow.getFirstSeenUsingPlayerId() != null) {
            this.playerRepository.findById(capeRow.getFirstSeenUsingPlayerId())
                    .ifPresent(p -> cape.setFirstSeenUsing(p.getUsername()));
        }

        List<String> usersOwning = this.playerRepository.findUsernamesByCapeId(capeRow.getId(), PageRequest.of(0, 250));
        cape.setAccountsSeenOwning(usersOwning);

        return cape;
    }

    public CapeRow getCapeByQuery(String query) {
        // By numeric ID
        if (!query.isEmpty() && query.chars().allMatch(Character::isDigit)) {
            Optional<CapeRow> optionalCapeRow = this.capeRepository.findById(Long.parseLong(query));
            if (optionalCapeRow.isEmpty()) {
                throw new NotFoundException("Cape not found");
            }
            return optionalCapeRow.get();
        }
        // By player (name or UUID)
        if (query.length() <= 36) {
            return this.playerService.getPlayer(query).getCape();
        }
        // By texture ID
        Optional<CapeRow> optionalCapeRow = this.capeRepository.findByTextureId(query);
        if (optionalCapeRow.isEmpty()) {
            throw new NotFoundException("Cape not found");
        }
        return optionalCapeRow.get();
    }

    /**
     * Cached variant of {@link #getOrCreateCape(CapeTextureToken, UUID)}.
     * Cache key is the texture ID only; {@code playerId} is only relevant on first insert.
     */
    @Cacheable(value = "capeByTextureId", key = "#token.textureId")
    @Transactional
    public CapeRow getOrCreateCapeCached(CapeTextureToken token, UUID playerId) {
        return getOrCreateCape(token, playerId);
    }

    @Transactional
    public CapeRow getOrCreateCape(CapeTextureToken token, UUID playerId) {
        Optional<CapeRow> optionalCapeRow = this.capeRepository.findByTextureId(token.getTextureId());
        if (optionalCapeRow.isPresent()) {
            return optionalCapeRow.get();
        }
        Optional<CapeRow> inserted = this.capeRepository.insertIfAbsent(null, token.getTextureId(), Instant.now(), playerId);
        if (inserted.isPresent()) {
            CapeRow newCape = inserted.get();
            StatisticsService.addTrackedCapeCount(1);
            try {
                DiscordWebhook discordWebhook = new DiscordWebhook(newCapeDiscoveredWebhook);
                discordWebhook.setUsername("New Cape Discovered");
                discordWebhook.addEmbed(new DiscordWebhook.EmbedObject()
                        .addField("Texture ID", token.getTextureId(), true)
                        .addField("Cape ID", String.valueOf(newCape.getId()), true)
                );
                discordWebhook.setContent("A new cape has been discovered! Check it out: %s/capes/%d".formatted(AppConfig.INSTANCE.getWebPublicUrl(), newCape.getId()));
                discordWebhook.execute();
            } catch (IOException ex) {
                log.warn(ex.getMessage());
            }
            return newCape;
        }
        return this.capeRepository.findByTextureId(token.getTextureId()).orElseThrow();
    }

    public Pagination.Page<VanillaCape> getPaginatedCapes(int page) {
        Pagination<VanillaCape> pagination = new Pagination<VanillaCape>().setItemsPerPage(CAPES_PER_PAGE).setTotalItems(this.statisticsService.getTrackedCapeCount());
        return pagination.getPage(page, (pageCallback) -> {
            Pageable pageable = PageRequest.of(page - 1, pageCallback.limit());
            return this.capeRepository.findAllOrderByUniqueOwnersDescIdAsc(pageable).map(VanillaCape::fromRow).stream().toList();
        });
    }

    /**
     * Gets the cape image for the given cape.
     *
     * @param cape the cape to get the image for
     * @return the cape image
     */
    public byte[] getCapeTexture(Cape<?> cape) {
        return this.textureLoader.get(cape.getTextureId(), () -> {
            long start = System.currentTimeMillis();
            String fileName = "%s.png".formatted(cape.getTextureId());
            byte[] stored = this.storageService.get(StorageService.Bucket.VANILLA_CAPES, fileName);
            if (stored != null) {
                return stored;
            }
            log.debug("Downloading cape image for cape {}", cape.getTextureId());
            byte[] bytes = webRequest.request(cape.getRawTextureUrl()).asBytes();
            if (bytes == null) {
                throw new IllegalStateException("Cape image for cape '%s' was not found".formatted(cape.getTextureId()));
            }
            log.debug("Downloaded cape image for cape {} in {}ms", cape.getTextureId(), System.currentTimeMillis() - start);
            this.storageService.upload(StorageService.Bucket.VANILLA_CAPES, fileName, MediaType.IMAGE_PNG_VALUE, bytes);
            return bytes;
        });
    }

    /**
     * Renders a cape part at the given size.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     *
     * @param cape     the cape to render
     * @param typeName the cape part type (e.g. FRONT)
     * @param size     the output size (height; width derived from cape aspect)
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

        StorageService.Bucket bucket = StorageService.Bucket.RENDERED_VANILLA_CAPES;

        long cacheStart = System.currentTimeMillis();
        byte[] canonicalBytes = cacheEnabled ? this.storageService.get(bucket, canonicalKey) : null;
        BufferedImage canonicalImage = null;

        if (canonicalBytes == null) {
            long renderStart = System.currentTimeMillis();
            canonicalImage = ((Cape) cape).render(part, maxPartSize, RenderOptions.DEFAULT);
            canonicalBytes = ImageUtils.imageToBytes(canonicalImage, 1);
            log.debug("Took {}ms to render cape part for cape: {}", System.currentTimeMillis() - renderStart, cape.getTextureId());
            MetricService.getMetric(CapeRenderMetric.class).recordMiss(System.currentTimeMillis() - renderStart);
            if (cacheEnabled) {
                final byte[] toUpload = canonicalBytes;
                CompletableFuture.runAsync(() -> this.storageService.upload(bucket, canonicalKey, MediaType.IMAGE_PNG_VALUE, toUpload), Main.EXECUTOR).exceptionally(ex -> {
                    log.warn("Save failed for cape part {}: {}", canonicalKey, ex.getMessage());
                    return null;
                });
            }
        } else {
            log.debug("Got cape part for cape {} from cache in {}ms", cape.getTextureId(), System.currentTimeMillis() - cacheStart);
            MetricService.getMetric(CapeRenderMetric.class).recordHit();
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = canonicalImage != null ? canonicalImage : ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }
}
