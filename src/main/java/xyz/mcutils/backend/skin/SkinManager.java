package xyz.mcutils.backend.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.metric.impl.skin.SkinCacheMetric;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.SkinRepository;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.StatisticsService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class SkinManager {

    public static SkinManager INSTANCE;

    private final SkinRepository skinRepository;
    private final MongoTemplate mongoTemplate;
    private final WebRequest webRequest;
    private final ConcurrentMap<String, UUID> textureIdToId = new ConcurrentHashMap<>();
    private final Cache<UUID, CachedSkinDocument> cacheById = CacheBuilder.newBuilder().maximumSize(1_000_000).removalListener(this::onEvictSkin).build();

    public SkinManager(SkinRepository skinRepository, MongoTemplate mongoTemplate, WebRequest webRequest) {
        this.skinRepository = skinRepository;
        this.mongoTemplate = mongoTemplate;
        this.webRequest = webRequest;
    }

    private void onEvictSkin(RemovalNotification<UUID, CachedSkinDocument> notification) {
        if (notification.getCause() == RemovalCause.SIZE || notification.getCause() == RemovalCause.EXPLICIT || notification.getCause() == RemovalCause.REPLACED) {
            CachedSkinDocument c = notification.getValue();
            if (c != null) {
                if (c.isDirty()) {
                    try {
                        SkinDocument doc = c.getDocument();
                        doc.setAccountsUsed(c.getAccountsUsed());
                        this.skinRepository.save(doc);
                    } catch (Exception e) {
                        log.warn("Failed to save skin on eviction {}", c.getDocument().getId(), e);
                    }
                }
                if (c.getDocument() != null && c.getDocument().getTextureId() != null) {
                    textureIdToId.remove(c.getDocument().getTextureId(), notification.getKey());
                }
            }
        }
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
        CachedSkinDocument cached = this.cacheById.getIfPresent(id);
        if (cached != null) {
            MetricService.getMetric(SkinCacheMetric.class).recordHit();
            return Optional.of(cached.snapshotDocument());
        }
        MetricService.getMetric(SkinCacheMetric.class).recordMiss();
        return this.skinRepository.findById(id).map(doc -> {
            put(doc);
            return doc;
        });
    }

    public long getCacheSize() {
        return this.cacheById.size();
    }

    /**
     * Gets multiple skins by id: cache-first, then a single bulk load from the repository for misses.
     * Returns a mutable map (UUID -> document); missing IDs are absent from the map.
     */
    public Map<UUID, SkinDocument> getByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, SkinDocument> result = new HashMap<>();
        List<UUID> missed = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            CachedSkinDocument cached = this.cacheById.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached.snapshotDocument());
            }
            else {
                missed.add(id);
            }
        }
        if (!missed.isEmpty()) {
            for (SkinDocument doc : this.skinRepository.findAllById(missed)) {
                UUID id = doc.getId();
                if (id != null) {
                    put(doc);
                    result.put(id, doc);
                }
            }
        }
        return result;
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
            CachedSkinDocument cached = this.cacheById.getIfPresent(id);
            if (cached != null) {
                return Optional.of(cached.snapshotDocument());
            }
            return this.getById(id);
        }
        return this.skinRepository.findByTextureId(textureId).map(doc -> {
            put(doc);
            return doc;
        });
    }

    /**
     * Gets or creates a skin by texture id. If missing, creates in memory and marks dirty for flush.
     */
    public SkinDocument getOrCreateByTextureId(SkinTextureToken token, UUID playerUuid) {
        Optional<SkinDocument> existing = this.getByTextureId(token.getTextureId());
        if (existing.isPresent()) {
            return existing.get();
        }
        SkinTextureToken.Metadata metadata = token.metadata();
        SkinDocument document = new SkinDocument(UUID.randomUUID(), token.getTextureId(), EnumUtils.getEnumConstant(Skin.Model.class, metadata == null ? "DEFAULT" : metadata.model()), Skin.isLegacySkin(Skin.CDN_URL.formatted(token.getTextureId()), this.webRequest), 0, playerUuid, Instant.now());
        StatisticsService.updateTrackedSkinCount(StatisticsService.INSTANCE.getTrackedSkinCount() + 1);
        put(document);
        CachedSkinDocument cached = this.cacheById.getIfPresent(document.getId());
        if (cached != null) {
            cached.setDirty(true);
        }
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
        CachedSkinDocument cached = this.cacheById.getIfPresent(skinId);
        if (cached == null) {
            cached = this.skinRepository.findById(skinId).map(doc -> {
                put(doc);
                return this.cacheById.getIfPresent(skinId);
            }).orElse(null);
        }
        if (cached != null) {
            cached.addAccountsUsed(delta);
        }
    }


    /**
     * Returns the number of cached skin documents pending save (dirty).
     */
    public long getDirtyCount() {
        return this.cacheById.asMap().values().stream().filter(CachedSkinDocument::isDirty).count();
    }

    /**
     * Flushes all dirty skin documents to MongoDB in one bulk replace.
     */
    public void flush() {
        List<SkinDocument> dirty = new ArrayList<>();
        for (CachedSkinDocument cached : this.cacheById.asMap().values()) {
            if (cached.isDirty()) {
                SkinDocument doc = cached.getDocument();
                doc.setAccountsUsed(cached.getAccountsUsed());
                dirty.add(doc);
                cached.setDirty(false);
            }
        }
        if (!dirty.isEmpty()) {
            try {
                MongoUtils.bulkReplaceUnordered(mongoTemplate, dirty, SkinDocument.class, SkinDocument::getId);
                log.debug("Flushed {} dirty skins", dirty.size());
            } catch (Exception e) {
                for (SkinDocument doc : dirty) {
                    CachedSkinDocument c = this.cacheById.getIfPresent(doc.getId());
                    if (c != null) {
                        c.setDirty(true);
                    }
                }
                log.warn("Bulk flush failed, re-marked {} skins dirty", dirty.size(), e);
            }
        }
    }

}
