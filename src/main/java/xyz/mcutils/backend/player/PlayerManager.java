package xyz.mcutils.backend.player;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.model.persistence.mongo.CapeHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.UsernameHistoryDocument;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for player documents. Cache-first lookups; periodic flush of dirty entries to MongoDB.
 * Uses a Guava cache with max 100k entries and a 30-minute TTL after last access; evicted dirty entries
 * are saved asynchronously to avoid blocking the eviction thread.
 */
@Component
@Slf4j
public class PlayerManager {

    public static PlayerManager INSTANCE;

    private final PlayerRepository playerRepository;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final MongoTemplate mongoTemplate;

    private final Cache<UUID, CachedPlayerDocument> cache = CacheBuilder.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener(this::onRemove)
            .build();

    public PlayerManager(PlayerRepository playerRepository, SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository, UsernameHistoryRepository usernameHistoryRepository, MongoTemplate mongoTemplate) {
        this.playerRepository = playerRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    private void onRemove(RemovalNotification<UUID, CachedPlayerDocument> notification) {
        if (notification.getCause() == RemovalCause.SIZE || notification.getCause() == RemovalCause.EXPLICIT || notification.getCause() == RemovalCause.REPLACED || notification.getCause() == RemovalCause.EXPIRED) {
            CachedPlayerDocument cached = notification.getValue();
            if (cached != null && cached.isDirty()) {
                PlayerDocument snapshot = cached.getDocument();
                Main.EXECUTOR.submit(() -> {
                    try {
                        this.playerRepository.save(snapshot);
                    } catch (Exception e) {
                        log.warn("Failed to save player on eviction {}", snapshot.getId(), e);
                    }
                });
            }
        }
    }

    @PostConstruct
    void init() {
        INSTANCE = this;
    }

    /**
     * Gets a player by UUID from cache or loads from the repository.
     */
    public Optional<PlayerDocument> getByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        CachedPlayerDocument cached = this.cache.getIfPresent(uuid);
        if (cached != null) {
            return Optional.of(cached.getDocument());
        }
        return this.playerRepository.findById(uuid).map(doc -> {
            this.cache.put(uuid, new CachedPlayerDocument(doc));
            return doc;
        });
    }

    /**
     * Gets multiple players by UUID: cache-first, then a single bulk load from the repository for misses.
     * Returns a mutable map (UUID -> document); missing IDs are absent from the map.
     */
    public Map<UUID, PlayerDocument> getByUuids(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, PlayerDocument> result = new HashMap<>();
        List<UUID> missed = new ArrayList<>();
        for (UUID uuid : uuids) {
            if (uuid == null) {
                continue;
            }
            CachedPlayerDocument cached = this.cache.getIfPresent(uuid);
            if (cached != null) {
                result.put(uuid, cached.getDocument());
            } else {
                missed.add(uuid);
            }
        }
        if (!missed.isEmpty()) {
            for (PlayerDocument doc : this.playerRepository.findAllById(missed)) {
                UUID id = doc.getId();
                if (id != null) {
                    this.cache.put(id, new CachedPlayerDocument(doc));
                    result.put(id, doc);
                }
            }
        }
        return result;
    }

    /**
     * Gets a player by username. Uses _id-only lookup then cache/load by UUID (avoids loading document refs).
     */
    public Optional<PlayerDocument> getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Query query = new Query(Criteria.where("username").is(username))
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()))
                .withHint("username_case_insensitive")
                .limit(1);
        List<Document> found = MongoUtils.findWithFields(this.mongoTemplate, query, PlayerDocument.class, "_id");
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Object idObj = found.getFirst().get("_id");
        if (!(idObj instanceof UUID uuid)) {
            return Optional.empty();
        }
        return this.getByUuid(uuid);
    }

    /**
     * Gets skin history for the player (ordered by last used descending).
     */
    public List<SkinHistoryDocument> getPlayerSkinHistory(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return skinHistoryRepository.findByPlayerIdOrderByLastUsedDesc(playerId);
    }

    /**
     * Gets cape history for the player (ordered by last used descending).
     */
    public List<CapeHistoryDocument> getPlayerCapeHistory(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return capeHistoryRepository.findByPlayerIdOrderByLastUsedDesc(playerId);
    }

    /**
     * Gets username history for the player (ordered by timestamp descending).
     */
    public List<UsernameHistoryDocument> getPlayerUsernameHistory(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return usernameHistoryRepository.findByPlayerIdOrderByTimestampDesc(playerId);
    }

    /**
     * Puts a document into the cache (e.g. after insert). Does not mark dirty.
     */
    public void put(PlayerDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }
        this.cache.put(document.getId(), new CachedPlayerDocument(document));
    }

    /**
     * Returns true if the player is in the cache (no DB read). Use to avoid loading when you only need existence.
     */
    public boolean isCached(UUID uuid) {
        return uuid != null && this.cache.getIfPresent(uuid) != null;
    }

    /**
     * Marks the cached player document as dirty so it will be persisted on next flush.
     * Use after updating a document obtained from {@link #getByUuid(UUID)} in place.
     */
    public void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CachedPlayerDocument cached = this.cache.getIfPresent(playerId);
        if (cached != null) {
            cached.setDirty(true);
        }
    }

    /**
     * Increments submittedUuids in memory and marks the entry dirty, or falls back to a
     * direct MongoDB $inc if the player is not currently cached.
     */
    public void incrementSubmittedUuids(UUID playerId, long delta) {
        if (playerId == null || delta <= 0) {
            return;
        }
        CachedPlayerDocument cached = this.cache.getIfPresent(playerId);
        if (cached != null) {
            cached.addSubmittedUuids(delta);
        } else {
            Query query = new Query(Criteria.where("_id").is(playerId));
            Update update = new Update().inc("submittedUuids", delta);
            mongoTemplate.updateFirst(query, update, PlayerDocument.class);
        }
    }

    /**
     * Returns the number of cached player documents pending save (dirty).
     */
    public long getDirtyCount() {
        return this.cache.asMap().values().stream().filter(CachedPlayerDocument::isDirty).count();
    }

    /**
     * Flushes all dirty player documents to MongoDB in one bulk replace.
     */
    public void flush() {
        List<PlayerDocument> dirty = new ArrayList<>();
        List<CachedPlayerDocument> dirtyEntries = new ArrayList<>();

        for (CachedPlayerDocument cached : this.cache.asMap().values()) {
            if (cached.isDirty()) {
                dirty.add(cached.getDocument());
                dirtyEntries.add(cached);
            }
        }

        if (!dirty.isEmpty()) {
            try {
                MongoUtils.bulkReplaceUnordered(mongoTemplate, dirty, PlayerDocument.class, PlayerDocument::getId);
                for (CachedPlayerDocument cached : dirtyEntries) {
                    cached.setDirty(false);
                }
                log.debug("Flushed {} dirty players", dirty.size());
            } catch (Exception e) {
                log.warn("Bulk flush failed, {} players remain dirty", dirty.size(), e);
            }
        }
    }
}