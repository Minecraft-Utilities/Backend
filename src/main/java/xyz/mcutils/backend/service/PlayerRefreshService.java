package xyz.mcutils.backend.service;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.skin.SkinManager;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 2_000;
    private final Semaphore refreshConcurrencyLimit = new Semaphore(15);

    private volatile boolean running = true;

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final MongoTemplate mongoTemplate;

    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService,
                                PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager,
                                SkinHistoryRepository skinHistoryRepository,
                                CapeHistoryRepository capeHistoryRepository,
                                UsernameHistoryRepository usernameHistoryRepository,
                                MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (running) {
                try {
                    List<UUID> ids = findRefreshChunkIds(REFRESH_CHUNK_SIZE);
                    if (ids.isEmpty()) {
                        for (int i = 0; i < 10 && running; i++) {
                            Thread.sleep(Duration.ofSeconds(1).toMillis());
                        }
                        continue;
                    }
                    Date batchTime = new Date();
                    List<UUID> refreshedIds = Collections.synchronizedList(new ArrayList<>());
                    BatchHistoryWriter historyWriter = new BatchHistoryWriter();
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
                                UUID skinId = playerDocument.getSkin() != null ? playerDocument.getSkin().getId() : null;
                                UUID capeId = playerDocument.getCape() != null ? playerDocument.getCape().getId() : null;
                                applyProfileToPlayer(playerDocument.getId(), playerDocument.getUsername(), skinId, capeId, token, playerDocument, batchTime, historyWriter);
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
                    historyWriter.flush(mongoTemplate, skinManager, capeManager);
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
    private List<UUID> findRefreshChunkIds(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.ASC, "lastUpdated"))
                .limit(limit);
        query.fields().include("_id");
        List<PlayerIdProjection> list = mongoTemplate.find(query, PlayerIdProjection.class, "players");
        return list.stream().map(PlayerIdProjection::getId).toList();
    }

    /**
     * Applies Mojang profile to the player: resolves skin/cape via managers, writes history, updates the given cached document in place and marks dirty.
     * When writer is null (e.g. updatePlayer), history is written immediately. When non-null (batch refresh), changes are collected for bulk flush.
     */
    private void applyProfileToPlayer(UUID playerId, String currentUsername, UUID currentSkinId, UUID currentCapeId,
                                      MojangProfileToken token, PlayerDocument doc, Date updatedAt,
                                      BatchHistoryWriter writer) {
        Date now = updatedAt != null ? updatedAt : new Date();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        // Resolve skin/cape via managers (get-or-create in cache)
        SkinDocument skinDoc = skinAndCape.left() != null
                ? skinManager.getOrCreateByTextureId(skinAndCape.left(), playerId)
                : null;
        UUID newSkinId = skinDoc != null ? skinDoc.getId() : currentSkinId;

        CapeDocument capeDoc = null;
        if (skinAndCape.right() != null) {
            try {
                capeDoc = capeManager.getOrCreateByTextureId(skinAndCape.right().getTextureId());
            } catch (Exception e) {
                log.debug("Cape resolve failed for player {}", playerId, e);
            }
        }
        UUID newCapeId = capeDoc != null ? capeDoc.getId() : currentCapeId;

        if (writer != null) {
            if (!Objects.equals(currentUsername, token.getName())) {
                writer.addUsernameChange(playerId, token.getName(), now);
            }
            if (newSkinId != null && !Objects.equals(newSkinId, currentSkinId)) {
                writer.addSkinChange(playerId, newSkinId, now);
            }
            if (newCapeId != null && !Objects.equals(newCapeId, currentCapeId)) {
                writer.addCapeChange(playerId, newCapeId, now);
            }
        } else {
            ensureUsernameHistory(playerId, currentUsername, token.getName(), now);
            if (newSkinId != null && !Objects.equals(newSkinId, currentSkinId) && ensureSkinHistory(playerId, newSkinId, now)) {
                skinManager.incrementAccountsUsed(newSkinId, 1);
            }
            if (newCapeId != null && !Objects.equals(newCapeId, currentCapeId) && ensureCapeHistory(playerId, newCapeId, now)) {
                capeManager.incrementAccountsOwned(newCapeId, 1);
            }
        }

        // Update cached player document in place
        doc.setUsername(token.getName());
        doc.setSkin(newSkinId != null ? SkinDocument.builder().id(newSkinId).build() : null);
        doc.setCape(newCapeId != null ? CapeDocument.builder().id(newCapeId).build() : null);
        doc.setLegacyAccount(token.isLegacy());
        doc.setLastUpdated(now);
        playerManager.markDirty(playerId);

        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
    }

    private void ensureUsernameHistory(UUID playerId, String currentUsername, String newName, Date now) {
        if (!Objects.equals(currentUsername, newName)) {
            usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(newName)
                    .timestamp(now)
                    .build());
        }
    }

    /**
     * Ensures a skin history entry exists for this player+skin. Call only when the skin has changed (newSkinId != currentSkinId).
     * Returns true if a new entry was inserted (caller should increment).
     */
    private boolean ensureSkinHistory(UUID playerId, UUID skinId, Date now) {
        Optional<SkinHistoryDocument> existing = skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinId);
        if (existing.isPresent()) {
            SkinHistoryDocument d = existing.get();
            d.setLastUsed(now);
            skinHistoryRepository.save(d);
            return false;
        }
        skinHistoryRepository.save(SkinHistoryDocument.builder()
                .id(UUID.randomUUID())
                .playerId(playerId)
                .skin(SkinDocument.builder().id(skinId).build())
                .lastUsed(now)
                .timestamp(now)
                .build());
        return true;
    }

    /**
     * Ensures a cape history entry exists for this player+cape. Call only when the cape has changed (newCapeId != currentCapeId).
     * Returns true if a new entry was inserted (caller should increment).
     */
    private boolean ensureCapeHistory(UUID playerId, UUID capeId, Date now) {
        Optional<CapeHistoryDocument> existing = capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeId);
        if (existing.isPresent()) {
            CapeHistoryDocument d = existing.get();
            d.setLastUsed(now);
            capeHistoryRepository.save(d);
            return false;
        }
        capeHistoryRepository.save(CapeHistoryDocument.builder()
                .id(UUID.randomUUID())
                .playerId(playerId)
                .cape(CapeDocument.builder().id(capeId).build())
                .lastUsed(now)
                .timestamp(now)
                .build());
        return true;
    }

    /**
     * Updates the in-memory player and document from the token; player document is updated in cache and marked dirty.
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        UUID playerId = document.getId();
        String currentUsername = document.getUsername();
        UUID currentSkinId = document.getSkin() != null ? document.getSkin().getId() : null;
        UUID currentCapeId = document.getCape() != null ? document.getCape().getId() : null;

        applyProfileToPlayer(playerId, currentUsername, currentSkinId, currentCapeId, token, document, null, null);

        // Reload from manager to get updated doc and sync to Player entity
        playerManager.getByUuid(playerId).ifPresent(doc -> {
            player.setUsername(doc.getUsername());
            player.setSkin(doc.getSkin() != null ? skinServiceToSkin(doc.getSkin().getId()) : null);
            player.setCape(doc.getCape() != null ? capeServiceToCape(doc.getCape().getId()) : null);
            player.setLegacyAccount(doc.isLegacyAccount());
            player.setLastUpdated(doc.getLastUpdated());
        });
    }

    private Skin skinServiceToSkin(UUID skinId) {
        return skinManager.getById(skinId).map(skinService::fromDocument).orElse(null);
    }

    private VanillaCape capeServiceToCape(UUID capeId) {
        return capeManager.getById(capeId).map(capeService::fromDocument).orElse(null);
    }
    
    /**
     * Stops the refresh loop (e.g. on shutdown before flush).
     */
    public void stop() {
        running = false;
    }

    /**
     * Id-only projection for player listing by lastUpdated.
     */
    @Document(collection = "players") @Getter
    private static class PlayerIdProjection {
        @Id
        private UUID id;
    }
}
