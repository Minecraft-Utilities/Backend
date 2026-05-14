package xyz.mcutils.backend.service;

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
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.cape.CapeRenderMetric;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.BasicPlayer;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.repository.postgres.CapeRepository;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class CapeService {
    public static final int CAPES_PER_PAGE = 50;
    public static CapeService INSTANCE;
    
    private final StorageService storageService;
    private final PlayerService playerService;
    private final CapeRepository capeRepository;
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

    public CapeService(StorageService storageService, @Lazy PlayerService playerService, CapeRepository capeRepository, WebRequest webRequest) {
        this.storageService = storageService;
        this.playerService = playerService;
        this.capeRepository = capeRepository;
        this.webRequest = webRequest;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public CapeRow getCapeById(long id) {
        Optional<CapeRow> optionalSkinRow = this.capeRepository.findById(id);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    public CapeRow getCapeByTextureIdOrPlayer(String query) {
        // By player
        if (query.length() <= 36) {
            BasicPlayer player = this.playerService.getPlayer(query);
            VanillaCape cape = player.getCape();
            if (cape == null) {
                return null;
            }
            return new CapeRow(cape.getName(), cape.getTextureId(), cape.getUniqueOwners(), cape.getFirstSeen());
        }
        Optional<CapeRow> optionalSkinRow = this.capeRepository.findByTextureId(query);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    @Transactional
    public CapeRow getOrCreateCape(CapeTextureToken token) {
        Optional<CapeRow> optionalSkinRow = this.capeRepository.findByTextureId(token.getTextureId());
        return optionalSkinRow.orElseGet(() -> this.capeRepository.save(new CapeRow(
                null,
                token.getTextureId(),
                0,
                Instant.now()
        )));
    }

    public Pagination.Page<VanillaCape> getPaginatedCapes(int page) {
        Pagination<VanillaCape> pagination = new Pagination<VanillaCape>().setItemsPerPage(CAPES_PER_PAGE).setTotalItems(this.capeRepository.count());
        return pagination.getPage(page, (pageCallback) -> {
            Pageable pageable = PageRequest.of(
                    page - 1,
                    pageCallback.limit()
            );
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
            byte[] bytes = webRequest.getAsByteArray(cape.getRawTextureUrl());
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
        }
        else {
            log.debug("Got cape part for cape {} from cache in {}ms", cape.getTextureId(), System.currentTimeMillis() - cacheStart);
            MetricService.getMetric(CapeRenderMetric.class).recordHit();
        }

        if (size == maxPartSize) {
            return canonicalBytes;
        }

        BufferedImage image = canonicalImage != null ? canonicalImage : ImageUtils.decodeImage(canonicalBytes);
        return ImageUtils.imageToBytes(ImageUtils.resizeToHeight(image, size), 1);
    }

//    /**
//     * Gets a paginated list of capes.
//     *
//     * @param page the page to get
//     * @return the paginated list of capes
//     */
//    public Pagination.Page<VanillaCape> getPaginatedCapes(int page) {
//        Pagination<VanillaCape> pagination = new Pagination<VanillaCape>().setItemsPerPage(CAPES_PER_PAGE).setTotalItems(this.getTrackedCapeCount());
//        return pagination.getPage(page, (pageCallback) -> {
//            Query q = new Query().with(PageRequest.of(page - 1, pageCallback.limit())).with(Sort.by(Sort.Order.desc("accountsOwned"), Sort.Order.asc("_id")));
//            List<Document> idDocs = MongoUtils.findWithFields(mongoTemplate, q, CapeDocument.class, "_id");
//            List<UUID> ids = idDocs.stream().map(doc -> doc.get("_id", UUID.class)).toList();
//            Map<UUID, CapeDocument> byId = capeManager.getByIds(ids);
//            return ids.stream().map(byId::get).filter(Objects::nonNull).map(this::fromDocument).toList();
//        });
//    }
//
//    /**
//     * Gets the DTO for the given cape.
//     *
//     * @param id the UUID of the cape
//     * @return the cape DTO
//     */
//    public VanillaCape getCape(UUID id) {
//        CapeDocument capeDocument = this.capeManager.getById(id).orElseThrow(() -> new NotFoundException("Cape with id '%s' not found".formatted(id)));
//        String firstSeenUsing = null;
//        if (capeDocument.getFirstPlayerSeenUsing() != null) {
//            Query firstQuery = Query.query(Criteria.where("_id").is(capeDocument.getFirstPlayerSeenUsing())).limit(1);
//            List<Document> firstDoc = MongoUtils.findWithFields(mongoTemplate, firstQuery, PlayerDocument.class, "_id", "username");
//            if (!firstDoc.isEmpty()) {
//                firstSeenUsing = firstDoc.getFirst().getString("username");
//            }
//        }
//        Query query = Query.query(Criteria.where("cape").is(capeDocument.getId())).with(PageRequest.of(0, 500));
//        List<String> accountsSeenOwning = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id", "username").stream().map(doc -> doc.getString("username")).toList();
//        VanillaCape cape = fromDocument(capeDocument);
//        cape.setFirstSeenUsing(firstSeenUsing);
//        cape.setAccountsSeenOwning(accountsSeenOwning);
//        return cape;
//    }
//
//    /**
//     * Gets all the known capes sorted by accounts owned.
//     * Queries DB for cape IDs only, then resolves each via cache/manager.
//     *
//     * @return the known capes
//     */
//    public Map<String, VanillaCape> getCapes() {
//        Map<String, VanillaCape> capes = new LinkedHashMap<>();
//        Query q = new Query().with(Sort.by(Sort.Order.desc("accountsOwned"), Sort.Order.asc("_id")));
//        List<Document> idDocs = MongoUtils.findWithFields(mongoTemplate, q, CapeDocument.class, "_id");
//        List<UUID> ids = idDocs.stream().map(doc -> doc.get("_id", UUID.class)).toList();
//        Map<UUID, CapeDocument> byId = capeManager.getByIds(ids);
//        for (UUID id : ids) {
//            CapeDocument cd = byId.get(id);
//            if (cd != null) {
//                VanillaCape c = fromDocument(cd);
//                capes.put(c.getTextureId(), c);
//            }
//        }
//        return capes;
//    }
//
//    /**
//     * Gets a cape by its id (cache or repository).
//     *
//     * @param id the cape document id
//     * @return the cape, or null if not found
//     */
//    public VanillaCape getCapeById(UUID id) {
//        if (id == null) {
//            return null;
//        }
//        return this.capeManager.getById(id).map(this::fromDocument).orElse(null);
//    }
//
//    /**
//     * Gets a cape by texture id (creates if valid and missing).
//     *
//     * @param textureId the cape to get
//     * @return the cape
//     * @throws NotFoundException if the cape does not exist and could not be created
//     */
//    public VanillaCape getCapeByTextureId(String textureId) {
//        if (textureId == null || textureId.isBlank()) {
//            return null;
//        }
//        return capeManager.getByTextureId(textureId).map(this::fromDocument).orElseGet(() -> fromDocument(capeManager.getOrCreateByTextureId(textureId, null)));
//    }
//
//    /**
//     * Gets a Cape from the texture id or the player's name / uuid.
//     *
//     * @param query the query to search for
//     * @return the cape, or null
//     */
//    public Cape<?> getCapeFromTextureIdOrPlayer(String query) {
//        Cape<?> cape;
//        // I really have no idea how long their sha-1 string length is
//        // a player name can't be more than 16 chars, so just assume it's a texture id
//        if (query.length() > 16) {
//            cape = this.getCapeByTextureId(query);
//        }
//        else {
//            Player player = this.playerService.getPlayer(query);
//            cape = player.getCape();
//            if (cape == null) {
//                throw new NotFoundException("Player '%s' does not have a cape equipped".formatted(player.getUsername()));
//            }
//        }
//        return cape;
//    }
//
//    /**
//     * Increments accountsOwned in memory for the given cape.
//     *
//     * @param capeId the cape document id
//     */
//    public void incrementAccountsOwned(UUID capeId) {
//        capeManager.incrementAccountsOwned(capeId, 1);
//    }
//
//
//    /**
//     * Converts a {@link CapeDocument} to a {@link VanillaCape}.
//     *
//     * @param document the document to convert
//     * @return the converted cape
//     */
//    public VanillaCape fromDocument(CapeDocument document) {
//        if (document == null) {
//            return null;
//        }
//        VanillaCape cape = new VanillaCape(document.getId(), document.getName(), document.getAccountsOwned(), document.getTextureId());
//        cape.setFirstSeen(document.getFirstSeen());
//        return cape;
//    }
//
//    /**
//     * Returns an estimated count of tracked capes for fast statistics.
//     */
//    public long getTrackedCapeCount() {
//        return this.mongoTemplate.estimatedCount(CapeDocument.class);
//    }
}
