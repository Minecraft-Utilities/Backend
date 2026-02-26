package xyz.mcutils.backend.skin;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.SkinRepository;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache for skin documents. Cache-first lookups; periodic flush of dirty entries to MongoDB.
 */
@Component
@Slf4j
public class SkinManager {

    public static SkinManager INSTANCE;

    private final SkinRepository skinRepository;
    private final WebRequest webRequest;
    private final ConcurrentMap<UUID, CachedSkinDocument> cacheById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> textureIdToId = new ConcurrentHashMap<>();

    public SkinManager(SkinRepository skinRepository, WebRequest webRequest) {
        this.skinRepository = skinRepository;
        this.webRequest = webRequest;
    }

    @PostConstruct
    void init() {
        INSTANCE = this;
    }

    /**
     * Gets a skin by id from cache or loads from the repository.
     */
    public Optional<SkinDocument> getById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        CachedSkinDocument cached = this.cacheById.get(id);
        if (cached != null) {
            return Optional.of(cached.snapshotDocument());
        }
        return this.skinRepository.findById(id)
                .map(doc -> {
                    put(doc);
                    return doc;
                });
    }

    /**
     * Gets a skin by texture id from cache or loads from the repository.
     */
    public Optional<SkinDocument> getByTextureId(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return Optional.empty();
        }
        UUID id = this.textureIdToId.get(textureId);
        if (id != null) {
            return this.getById(id);
        }
        return this.skinRepository.findByTextureId(textureId)
                .map(doc -> {
                    put(doc);
                    return doc;
                });
    }

    /**
     * Gets or creates a skin by texture id. If missing, inserts and puts in cache.
     */
    public SkinDocument getOrCreateByTextureId(SkinTextureToken token, UUID playerUuid) {
        Optional<SkinDocument> existing = this.getByTextureId(token.getTextureId());
        if (existing.isPresent()) {
            return existing.get();
        }
        SkinTextureToken.Metadata metadata = token.metadata();
        SkinDocument document = this.skinRepository.insert(new SkinDocument(
                UUID.randomUUID(),
                token.getTextureId(),
                EnumUtils.getEnumConstant(Skin.Model.class, metadata == null ? "DEFAULT" : metadata.model()),
                Skin.isLegacySkin(Skin.CDN_URL.formatted(token.getTextureId()), this.webRequest),
                0,
                playerUuid,
                new Date()
        ));
        StatisticsService.updateTrackedSkinCount(StatisticsService.INSTANCE.getTrackedSkinCount() + 1);
        put(document);
        log.debug("Created skin {}", document.getTextureId());
        return document;
    }

    /**
     * Puts a document into the cache (e.g. after insert). Does not mark dirty.
     */
    public void put(SkinDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        CachedSkinDocument cached = new CachedSkinDocument(document);
        this.cacheById.put(document.getId(), cached);
        if (document.getTextureId() != null) {
            this.textureIdToId.put(document.getTextureId(), document.getId());
        }
    }

    /**
     * Increments accountsUsed in memory and marks the entry dirty.
     */
    public void incrementAccountsUsed(UUID skinId, long delta) {
        if (skinId == null || delta <= 0) {
            return;
        }
        CachedSkinDocument cached = this.cacheById.get(skinId);
        if (cached == null) {
            cached = this.skinRepository.findById(skinId)
                    .map(doc -> {
                        put(doc);
                        return this.cacheById.get(skinId);
                    })
                    .orElse(null);
        }
        if (cached != null) {
            cached.addAccountsUsed(delta);
        }
    }

    
    /**
     * Returns the number of cached skin documents pending save (dirty).
     */
    public long getDirtyCount() {
        return this.cacheById.values().stream().filter(CachedSkinDocument::isDirty).count();
    }

    /**
     * Flushes all dirty skin documents to MongoDB.
     */
    public void flush() {
        int saved = 0;
        for (CachedSkinDocument cached : this.cacheById.values()) {
            if (cached.isDirty()) {
                try {
                    SkinDocument doc = cached.getDocument();
                    doc.setAccountsUsed(cached.getAccountsUsed());
                    this.skinRepository.save(doc);
                    cached.setDirty(false);
                    saved++;
                } catch (Exception e) {
                    log.warn("Failed to flush skin {}", cached.getDocument().getId(), e);
                }
            }
        }
        if (saved > 0) {
            log.debug("Flushed {} dirty skins", saved);
        }
    }

}
