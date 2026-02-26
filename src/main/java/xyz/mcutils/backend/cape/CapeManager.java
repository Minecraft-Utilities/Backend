package xyz.mcutils.backend.cape;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache for cape documents. Cache-first lookups; periodic flush of dirty entries to MongoDB.
 */
@Component
@Slf4j
public class CapeManager {

    public static CapeManager INSTANCE;

    private final CapeRepository capeRepository;
    private final WebRequest webRequest;
    private final ConcurrentMap<UUID, CachedCapeDocument> cacheById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> textureIdToId = new ConcurrentHashMap<>();

    public CapeManager(CapeRepository capeRepository, WebRequest webRequest) {
        this.capeRepository = capeRepository;
        this.webRequest = webRequest;
    }

    @PostConstruct
    void init() {
        INSTANCE = this;
    }

    /**
     * Gets a cape by id from cache or loads from the repository.
     */
    public Optional<CapeDocument> getById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        CachedCapeDocument cached = this.cacheById.get(id);
        if (cached != null) {
            return Optional.of(cached.snapshotDocument());
        }
        return this.capeRepository.findById(id)
                .map(doc -> {
                    put(doc);
                    return this.cacheById.get(id).snapshotDocument();
                });
    }

    /**
     * Gets a cape by texture id from cache or loads from the repository.
     */
    public Optional<CapeDocument> getByTextureId(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return Optional.empty();
        }
        UUID id = this.textureIdToId.get(textureId);
        if (id != null) {
            return this.getById(id);
        }
        return this.capeRepository.findByTextureId(textureId)
                .map(doc -> {
                    put(doc);
                    return this.cacheById.get(doc.getId()).snapshotDocument();
                });
    }

    /**
     * Gets or creates a cape by texture id. If missing, validates existence then inserts and puts in cache.
     *
     * @throws NotFoundException if the cape does not exist and cannot be created
     */
    public CapeDocument getOrCreateByTextureId(String textureId) {
        Optional<CapeDocument> existing = this.getByTextureId(textureId);
        if (existing.isPresent()) {
            return existing.get();
        }
        boolean exists = false;
        try {
            exists = VanillaCape.capeExists(textureId, this.webRequest).get();
        } catch (Exception ex) {
            log.debug("Cape existence check failed for textureId {}", textureId, ex);
        }
        if (!exists) {
            throw new NotFoundException("Cape with texture id " + textureId + " was not found");
        }
        CapeDocument document = this.capeRepository.insert(new CapeDocument(
                UUID.randomUUID(),
                null,
                textureId,
                0,
                new Date()
        ));
        StatisticsService.updateTrackedCapeCount(StatisticsService.INSTANCE.getTrackedCapeCount() + 1);
        put(document);
        log.debug("Created cape {}", document.getTextureId());
        return document;
    }

    /**
     * Puts a document into the cache (e.g. after insert). Does not mark dirty.
     */
    public void put(CapeDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        CachedCapeDocument cached = new CachedCapeDocument(document);
        this.cacheById.put(document.getId(), cached);
        if (document.getTextureId() != null) {
            this.textureIdToId.put(document.getTextureId(), document.getId());
        }
    }

    /**
     * Increments accountsOwned in memory and marks the entry dirty.
     */
    public void incrementAccountsOwned(UUID capeId, long delta) {
        if (capeId == null || delta <= 0) {
            return;
        }
        CachedCapeDocument cached = this.cacheById.get(capeId);
        if (cached == null) {
            cached = this.capeRepository.findById(capeId)
                    .map(doc -> {
                        put(doc);
                        return this.cacheById.get(capeId);
                    })
                    .orElse(null);
        }
        if (cached != null) {
            cached.addAccountsOwned(delta);
        }
    }

    /**
     * Returns the number of cached cape documents pending save (dirty).
     */
    public long getDirtyCount() {
        return this.cacheById.values().stream().filter(CachedCapeDocument::isDirty).count();
    }

    /**
     * Flushes all dirty cape documents to MongoDB.
     */
    public void flush() {
        int saved = 0;
        for (CachedCapeDocument cached : this.cacheById.values()) {
            if (cached.isDirty()) {
                try {
                    CapeDocument doc = cached.getDocument();
                    doc.setAccountsOwned(cached.getAccountsOwned());
                    this.capeRepository.save(doc);
                    cached.setDirty(false);
                    saved++;
                } catch (Exception e) {
                    log.warn("Failed to flush cape {}", cached.getDocument().getId(), e);
                }
            }
        }
        if (saved > 0) {
            log.debug("Flushed {} dirty capes", saved);
        }
    }

}
