package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.common.FutureUtils;
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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 5_000;
    private static final Semaphore REFRESH_CONCURRENCY_LIMIT = new Semaphore(5);

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final PlayerHistoryService playerHistoryService;
    private final MongoTemplate mongoTemplate;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager, PlayerHistoryService playerHistoryService, MongoTemplate mongoTemplate) {
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
            while (running.get()) {
                try {
                    List<UUID> ids = findRefreshChunkIds();
                    if (ids.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10).toMillis());
                        continue;
                    }
                    Date batchTime = new Date();
                    Queue<UUID> refreshedIds = new ConcurrentLinkedQueue<>();
                    Map<UUID, PlayerDocument> playerMap = this.playerManager.getByUuids(ids);
                    List<Future<?>> futures = new ArrayList<>();
                    for (UUID playerId : ids) {
                        futures.add(Main.EXECUTOR.submit(() -> {
                            if (!running.get()) {
                                return;
                            }
                            PlayerDocument playerDocument = playerMap.get(playerId);
                            if (playerDocument == null) {
                                return;
                            }
                            try {
                                REFRESH_CONCURRENCY_LIMIT.acquire();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            try {
                                MojangProfileToken token = this.mojangService.getProfile(playerDocument.getId().toString());
                                if (token == null) {
                                    return;
                                }
                                UUID skinId = playerDocument.getSkinId();
                                UUID capeId = playerDocument.getCapeId();
                                applyProfileToPlayer(playerDocument.getId(), skinId, capeId, token, playerDocument, batchTime);
                                refreshedIds.add(playerDocument.getId());
                            } finally {
                                REFRESH_CONCURRENCY_LIMIT.release();
                            }
                        }));
                    }
                    FutureUtils.awaitAll(futures, "player refresh");
                    if (!refreshedIds.isEmpty()) {
                        MongoUtils.bulkSetUnordered(mongoTemplate, PlayerDocument.class, new ArrayList<>(refreshedIds), "lastUpdated", batchTime);
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

    private record ResolvedAssets(UUID skinId, UUID capeId) {}

    /**
     * Resolves the skin and cape documents from the Mojang profile token.
     * Distinguishes between Mojang returning null (keep current) and resolution failure (log and keep current).
     */
    private ResolvedAssets resolveAssets(UUID playerId, UUID currentSkinId, UUID currentCapeId, MojangProfileToken token) {
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        UUID newSkinId;
        if (skinAndCape.left() != null) {
            SkinDocument skinDoc = skinManager.getOrCreateByTextureId(skinAndCape.left(), playerId);
            newSkinId = skinDoc != null ? skinDoc.getId() : currentSkinId;
        } else {
            if (currentSkinId != null) {
                log.debug("Mojang profile had no skin for player {}; keeping current skin {}", playerId, currentSkinId);
            }
            newSkinId = currentSkinId;
        }

        UUID newCapeId;
        if (skinAndCape.right() != null) {
            try {
                CapeDocument capeDoc = capeManager.getOrCreateByTextureId(skinAndCape.right().getTextureId());
                newCapeId = capeDoc != null ? capeDoc.getId() : currentCapeId;
            } catch (Exception e) {
                log.debug("Cape resolve failed for player {}; keeping current cape {}", playerId, e);
                newCapeId = currentCapeId;
            }
        } else {
            if (currentCapeId != null) {
                log.debug("Mojang profile had no cape for player {}; keeping current cape {}", playerId, currentCapeId);
            }
            newCapeId = currentCapeId;
        }

        return new ResolvedAssets(newSkinId, newCapeId);
    }

    /**
     * Writes username, skin, and cape history entries for the player, incrementing usage counters on first-seen entries.
     */
    private void writeHistory(UUID playerId, String name, ResolvedAssets assets, Date now) {
        playerHistoryService.ensureUsernameInHistory(playerId, name, now);
        if (assets.skinId() != null && playerHistoryService.ensureSkinInHistory(playerId, assets.skinId(), now)) {
            skinManager.incrementAccountsUsed(assets.skinId(), 1);
        }
        if (assets.capeId() != null && playerHistoryService.ensureCapeInHistory(playerId, assets.capeId(), now)) {
            capeManager.incrementAccountsOwned(assets.capeId(), 1);
        }
    }

    /**
     * Patches the player document fields in place and marks it dirty for flushing.
     */
    private void patchDocument(PlayerDocument doc, String name, boolean legacyAccount, ResolvedAssets assets, Date now) {
        doc.setUsername(name);
        doc.setSkinId(assets.skinId());
        doc.setCapeId(assets.capeId());
        doc.setLegacyAccount(legacyAccount);
        doc.setLastUpdated(now);
        playerManager.markDirty(doc.getId());
        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
    }

    /**
     * Applies Mojang profile to the player document: resolves assets, writes history, and patches the document.
     */
    private void applyProfileToPlayer(UUID playerId, UUID currentSkinId, UUID currentCapeId, MojangProfileToken token, PlayerDocument doc, Date updatedAt) {
        Date now = updatedAt != null ? updatedAt : new Date();
        ResolvedAssets assets = resolveAssets(playerId, currentSkinId, currentCapeId, token);
        writeHistory(playerId, token.getName(), assets, now);
        patchDocument(doc, token.getName(), token.isLegacy(), assets, now);
    }

    /**
     * Updates the in-memory player and document from the token; player document is updated in cache and marked dirty.
     *
     * @param player   the player
     * @param document the player document
     * @param token    the mojang profile token
     */
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        UUID playerId = document.getId();
        UUID currentSkinId = document.getSkinId();
        UUID currentCapeId = document.getCapeId();

        applyProfileToPlayer(playerId, currentSkinId, currentCapeId, token, document, null);

        // Reload from manager to get updated doc and sync to Player entity
        playerManager.getByUuid(playerId).ifPresent(updated -> {
            player.setUsername(updated.getUsername());
            player.setSkin(updated.getSkinId() != null ? skinManager.getById(updated.getSkinId()).map(skinService::fromDocument).orElse(null) : null);
            player.setCape(updated.getCapeId() != null ? capeManager.getById(updated.getCapeId()).map(capeService::fromDocument).orElse(null) : null);
            player.setLegacyAccount(updated.isLegacyAccount());
            player.setLastUpdated(updated.getLastUpdated());
        });
    }

    /**
     * Stops the refresh loop (e.g. on shutdown before flush).
     */
    public void stop() {
        running.set(false);
    }

}
