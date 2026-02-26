package xyz.mcutils.backend.cape;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.repository.mongo.CapeRepository;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache for cape documents. Cache-first lookups; periodic flush of dirty entries to MongoDB.
 * Uses a Guava cache with max 50k entries; oldest (least recently used) entries are evicted when full.
 */
@Component
@Slf4j
public class CapeManager {

    public static CapeManager INSTANCE;

    private final CapeRepository capeRepository;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;
    private final ConcurrentMap<String, UUID> textureIdToId = new ConcurrentHashMap<>();
    private final Cache<UUID, CachedCapeDocument> cacheById = CacheBuilder.newBuilder()
            .build();

    public CapeManager(CapeRepository capeRepository, MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.capeRepository = capeRepository;
        this.mongoTemplate = mongoTemplate;
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
        CachedCapeDocument cached = this.cacheById.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached.snapshotDocument());
        }
        return this.capeRepository.findById(id)
                .map(doc -> {
                    put(doc);
                    return this.cacheById.getIfPresent(id).snapshotDocument();
                });
    }

    /**
     * Gets multiple capes by id: cache-first, then a single bulk load from the repository for misses.
     * Returns a mutable map (UUID -> document); missing IDs are absent from the map.
     */
    public Map<UUID, CapeDocument> getByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, CapeDocument> result = new HashMap<>();
        List<UUID> missed = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            CachedCapeDocument cached = this.cacheById.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached.snapshotDocument());
            } else {
                missed.add(id);
            }
        }
        if (!missed.isEmpty()) {
            for (CapeDocument doc : this.capeRepository.findAllById(missed)) {
                UUID id = doc.getId();
                if (id != null) {
                    put(doc);
                    result.put(id, this.cacheById.getIfPresent(id).snapshotDocument());
                }
            }
        }
        return result;
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
            CachedCapeDocument cached = this.cacheById.getIfPresent(id);
            if (cached != null) {
                return Optional.of(cached.snapshotDocument());
            }
            return this.getById(id);
        }
        return this.capeRepository.findByTextureId(textureId)
                .map(doc -> {
                    put(doc);
                    return this.cacheById.getIfPresent(doc.getId()).snapshotDocument();
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
        CachedCapeDocument cached = this.cacheById.getIfPresent(capeId);
        if (cached == null) {
            cached = this.capeRepository.findById(capeId)
                    .map(doc -> {
                        put(doc);
                        return this.cacheById.getIfPresent(capeId);
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
        return this.cacheById.asMap().values().stream().filter(CachedCapeDocument::isDirty).count();
    }

    /**
     * Flushes all dirty cape documents to MongoDB in one bulk replace.
     */
    public void flush() {
        List<CapeDocument> dirty = new ArrayList<>();
        for (CachedCapeDocument cached : this.cacheById.asMap().values()) {
            if (cached.isDirty()) {
                CapeDocument doc = cached.getDocument();
                doc.setAccountsOwned(cached.getAccountsOwned());
                dirty.add(doc);
                cached.setDirty(false);
            }
        }
        if (!dirty.isEmpty()) {
            try {
                MongoUtils.bulkReplaceUnordered(mongoTemplate, dirty, CapeDocument.class, CapeDocument::getId);
                log.debug("Flushed {} dirty capes", dirty.size());
            } catch (Exception e) {
                for (CapeDocument doc : dirty) {
                    CachedCapeDocument c = this.cacheById.getIfPresent(doc.getId());
                    if (c != null) {
                        c.setDirty(true);
                    }
                }
                log.warn("Bulk flush failed, re-marked {} capes dirty", dirty.size(), e);
            }
        }
    }

}
