package xyz.mcutils.backend.player;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory cache for player documents. Cache-first lookups; periodic flush of dirty entries to MongoDB.
 * Uses a Guava cache with max 50k entries; oldest (least recently used) entries are evicted when full.
 */
@Component
@Slf4j
public class PlayerManager {

    public static PlayerManager INSTANCE;

    private final PlayerRepository playerRepository;
    private final Cache<UUID, CachedPlayerDocument> cache = CacheBuilder.newBuilder()
            .maximumSize(100_000)
            .removalListener(this::onRemove)
            .build();

    private void onRemove(RemovalNotification<UUID, CachedPlayerDocument> notification) {
        if (notification.getCause() == RemovalCause.SIZE || notification.getCause() == RemovalCause.EXPLICIT
                || notification.getCause() == RemovalCause.REPLACED) {
            CachedPlayerDocument cached = notification.getValue();
            if (cached != null && cached.isDirty()) {
                try {
                    this.playerRepository.save(cached.getDocument());
                } catch (Exception e) {
                    log.warn("Failed to save player on eviction {}", cached.getDocument().getId(), e);
                }
            }
        }
    }

    public PlayerManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
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
        return this.playerRepository.findById(uuid)
                .map(doc -> {
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
     * Gets a player by username. Uses repository for username lookup then cache/load by UUID.
     */
    public Optional<PlayerDocument> getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<PlayerDocument> list = this.playerRepository.usernameToUuid(username);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return this.getByUuid(list.getFirst().getId());
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
     * Increments submittedUuids in memory and marks the entry dirty.
     */
    public void incrementSubmittedUuids(UUID playerId, long delta) {
        if (playerId == null || delta <= 0) {
            return;
        }
        CachedPlayerDocument cached = this.cache.getIfPresent(playerId);
        if (cached == null) {
            cached = this.playerRepository.findById(playerId)
                    .map(doc -> {
                        CachedPlayerDocument c = new CachedPlayerDocument(doc);
                        this.cache.put(playerId, c);
                        return c;
                    })
                    .orElse(null);
        }
        if (cached != null) {
            cached.addSubmittedUuids(delta);
        }
    }

    /**
     * Returns the number of cached player documents pending save (dirty).
     */
    public long getDirtyCount() {
        return this.cache.asMap().values().stream().filter(CachedPlayerDocument::isDirty).count();
    }

    /**
     * Flushes all dirty player documents to MongoDB.
     */
    public void flush() {
        int saved = 0;
        for (CachedPlayerDocument cached : this.cache.asMap().values()) {
            if (cached.isDirty()) {
                try {
                    this.playerRepository.save(cached.getDocument());
                    cached.setDirty(false);
                    saved++;
                } catch (Exception e) {
                    log.warn("Failed to flush player {}", cached.getDocument().getId(), e);
                }
            }
        }
        if (saved > 0) {
            log.debug("Flushed {} dirty players", saved);
        }
    }
}
