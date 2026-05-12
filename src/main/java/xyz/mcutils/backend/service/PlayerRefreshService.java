package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.player.PlayerManager;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 50_000;
    
    private final RateLimiter rateLimiter = RateLimiter.create(40);
    private final MojangService mojangService;
    private final PlayerManager playerManager;
    private final PlayerService playerService;
    private final MongoTemplate mongoTemplate;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(MojangService mojangService, PlayerManager playerManager, PlayerService playerService, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.playerManager = playerManager;
        this.playerService = playerService;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (running.get()) {
                try {
                    List<UUID> ids = findRefreshChunkIds();
                    if (ids.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    Date batchTime = new Date();
                    Map<UUID, PlayerDocument> playerMap = this.playerManager.getByUuids(ids);
                    List<Future<?>> futures = new ArrayList<>();
                    for (UUID playerId : ids) {
                        rateLimiter.acquire();
                        futures.add(Main.EXECUTOR.submit(() -> {
                            if (!running.get()) {
                                return;
                            }
                            PlayerDocument playerDocument = playerMap.get(playerId);
                            if (playerDocument == null) {
                                return;
                            }
                            MojangProfileToken token = this.mojangService.getProfile(playerDocument.getId().toString());
                            if (token == null) {
                                return;
                            }
                            UUID skinId = playerDocument.getSkinId();
                            UUID capeId = playerDocument.getCapeId();
                            this.playerService.applyProfileToPlayer(playerDocument.getId(), skinId, capeId, token, playerDocument, batchTime);
                        }));
                    }
                    FutureUtils.awaitAll(futures, "player refresh");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Returns player ids with oldest lastUpdated first (projection _id only). Use manager to load full document.
     */
    private List<UUID> findRefreshChunkIds() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "lastUpdated")).limit(PlayerRefreshService.REFRESH_CHUNK_SIZE);
        List<Document> found = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id");
        return found.stream()
                .map(doc -> doc.get("_id"))
                .filter(id -> {
                    if (!(id instanceof UUID)) {
                        log.warn("Unexpected non-UUID _id in players collection: {} ({})", id, id == null ? "null" : id.getClass().getName());
                        return false;
                    }
                    return true;
                })
                .map(UUID.class::cast)
                .toList();
    }

    /**
     * Stops the refresh loop (e.g. on shutdown before flush).
     */
    public void stop() {
        running.set(false);
    }
}
