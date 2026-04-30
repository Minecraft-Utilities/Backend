package xyz.mcutils.backend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.skin.SkinManager;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 2_000;
    private final Semaphore refreshConcurrencyLimit = new Semaphore(3);

    private volatile boolean running = true;

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final PlayerHistoryService playerHistoryService;
    private final MongoTemplate mongoTemplate;

    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService,
                                PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager,
                                PlayerHistoryService playerHistoryService, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
        this.playerHistoryService = playerHistoryService;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (running) {
                try {
                    List<UUID> ids = findRefreshChunkIds();
                    if (ids.isEmpty()) {
                        for (int i = 0; i < 10 && running; i++) {
                            Thread.sleep(Duration.ofSeconds(1).toMillis());
                        }
                        continue;
                    }
                    Date batchTime = new Date();
                    List<UUID> refreshedIds = Collections.synchronizedList(new ArrayList<>());
                    Map<UUID, PlayerDocument> playerMap = this.playerManager.getByUuids(ids);
                    List<Future<?>> futures = new ArrayList<>();
                    for (UUID playerId : ids) {
                        futures.add(Main.EXECUTOR.submit(() -> {
                            PlayerDocument playerDocument = playerMap.get(playerId);
                            if (playerDocument == null) {
                                return;
                            }
                            refreshConcurrencyLimit.acquireUninterruptibly();
                            try {
                                MojangProfileToken token = this.mojangService.getProfile(playerDocument.getId().toString());
                                if (token == null) {
                                    return;
                                }
                                UUID skinId = playerDocument.getSkinId();
                                UUID capeId = playerDocument.getCapeId();
                                applyProfileToPlayer(playerDocument.getId(), playerDocument.getUsername(), skinId, capeId, token, playerDocument, batchTime);
                                refreshedIds.add(playerDocument.getId());
                            } finally {
                                refreshConcurrencyLimit.release();
                            }
                        }));
                    }
                    for (Future<?> f : futures) {
                        try {
                            f.get();
                        } catch (ExecutionException e) {
                            log.warn("Player refresh failed", e.getCause());
                        }
                    }
                    if (!refreshedIds.isEmpty()) {
                        MongoUtils.bulkSetUnordered(mongoTemplate, PlayerDocument.class, refreshedIds, "lastUpdated", batchTime);
                    }
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
        Query query = new Query()
                .with(Sort.by(Sort.Direction.ASC, "lastUpdated"))
                .limit(PlayerRefreshService.REFRESH_CHUNK_SIZE);
        List<Document> found = MongoUtils.findWithFields(mongoTemplate, query, PlayerDocument.class, "_id");
        return found.stream()
                .map(doc -> doc.get("_id"))
                .filter(id -> id instanceof UUID)
                .map(UUID.class::cast)
                .toList();
    }

    /**
     * Applies Mojang profile to the player: resolves skin/cape via managers, writes history, updates the given cached document in place and marks dirty.
     */
    private void applyProfileToPlayer(UUID playerId, String currentUsername, UUID currentSkinId, UUID currentCapeId, MojangProfileToken token, PlayerDocument doc, Date updatedAt) {
        Date now = updatedAt != null ? updatedAt : new Date();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        // Resolve skin/cape via managers (get-or-create in cache)
        SkinDocument skinDoc = skinAndCape.left() != null
                ? skinManager.getOrCreateByTextureId(skinAndCape.left(), playerId)
                : null;
        UUID newSkinId = skinDoc != null ? skinDoc.getId() : currentSkinId;
        if (skinAndCape.left() == null && currentSkinId != null) {
            log.debug("Mojang profile had no skin for player {}; keeping current skin {}", playerId, currentSkinId);
        }

        CapeDocument capeDoc = null;
        if (skinAndCape.right() != null) {
            try {
                capeDoc = capeManager.getOrCreateByTextureId(skinAndCape.right().getTextureId());
            } catch (Exception e) {
                log.debug("Cape resolve failed for player {}", playerId, e);
            }
        }
        UUID newCapeId = capeDoc != null ? capeDoc.getId() : currentCapeId;
        if (skinAndCape.right() == null && currentCapeId != null) {
            log.debug("Mojang profile had no cape for player {}; keeping current cape {}", playerId, currentCapeId);
        }

        // Shared ensure-history path: never ignore missing history
        playerHistoryService.ensureUsernameInHistory(playerId, token.getName(), now);
        if (newSkinId != null && playerHistoryService.ensureSkinInHistory(playerId, newSkinId, now)) {
            skinManager.incrementAccountsUsed(newSkinId, 1);
        }
        if (newCapeId != null && playerHistoryService.ensureCapeInHistory(playerId, newCapeId, now)) {
            capeManager.incrementAccountsOwned(newCapeId, 1);
        }

        // Update cached player document in place
        doc.setUsername(token.getName());
        doc.setSkinId(newSkinId);
        doc.setCapeId(newCapeId);
        doc.setLegacyAccount(token.isLegacy());
        doc.setLastUpdated(now);
        playerManager.markDirty(playerId);

        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
    }

    /**
     * Updates the in-memory player and document from the token; player document is updated in cache and marked dirty.
     * 
     * @param player the player
     * @param document the player document
     * @param token the mojang profile token
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        UUID playerId = document.getId();
        String currentUsername = document.getUsername();
        UUID currentSkinId = document.getSkinId();
        UUID currentCapeId = document.getCapeId();

        applyProfileToPlayer(playerId, currentUsername, currentSkinId, currentCapeId, token, document, null);

        // Reload from manager to get updated doc and sync to Player entity
        playerManager.getByUuid(playerId).ifPresent(doc -> {
            player.setUsername(doc.getUsername());
            player.setSkin(doc.getSkinId() != null ? skinManager.getById(doc.getSkinId()).map(skinService::fromDocument).orElse(null) : null);
            player.setCape(doc.getCapeId() != null ? capeManager.getById(doc.getCapeId()).map(capeService::fromDocument).orElse(null) : null);
            player.setLegacyAccount(doc.isLegacyAccount());
            player.setLastUpdated(doc.getLastUpdated());
        });
    }
    
    /**
     * Stops the refresh loop (e.g. on shutdown before flush).
     */
    public void stop() {
        running = false;
    }

}
