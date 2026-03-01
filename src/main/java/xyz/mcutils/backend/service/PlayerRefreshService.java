package xyz.mcutils.backend.service;

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
import xyz.mcutils.backend.model.persistence.mongo.CapeHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.UsernameHistoryDocument;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;
import xyz.mcutils.backend.skin.SkinManager;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 2_000;
    private final Semaphore refreshConcurrencyLimit = new Semaphore(20);

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
                                SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository,
                                UsernameHistoryRepository usernameHistoryRepository, MongoTemplate mongoTemplate) {
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
                                UUID skinId = playerDocument.getSkin() != null ? playerDocument.getSkin().getId() : null;
                                UUID capeId = playerDocument.getCape() != null ? playerDocument.getCape().getId() : null;
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

        boolean skinChanged = !Objects.equals(newSkinId, currentSkinId);
        boolean capeChanged = !Objects.equals(newCapeId, currentCapeId);

        if (!Objects.equals(currentUsername, token.getName())) {
            insertUsernameHistory(playerId, currentUsername, token.getName(), now);
        }
        if (newSkinId != null && insertSkinHistory(playerId, newSkinId, now, skinChanged)) {
            skinManager.incrementAccountsUsed(newSkinId, 1);
        }
        if (newCapeId != null && insertCapeHistory(playerId, newCapeId, now, capeChanged)) {
            capeManager.incrementAccountsOwned(newCapeId, 1);
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

    /**
     * Inserts username history for this player.
     * 
     * @param playerId the player id
     * @param currentUsername the current username
     * @param newName the new username
     * @param now the timestamp
     */
    private void insertUsernameHistory(UUID playerId, String currentUsername, String newName, Date now) {
        usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(newName)
                    .timestamp(now)
                    .build());
    }

    /**
     * Inserts skin history for this player.
     * 
     * @param playerId the player id
     * @param skinId the skin id
     * @param now the timestamp
     * @param updateLastUsed whether to update the last used timestamp
     * @return true if a new entry was inserted, false if an existing entry was updated
     */
    private boolean insertSkinHistory(UUID playerId, UUID skinId, Date now, boolean updateLastUsed) {
        Optional<SkinHistoryDocument> existing = skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinId);
        if (existing.isPresent() && updateLastUsed) {
            SkinHistoryDocument d = existing.get();
            d.setLastUsed(now);
            skinHistoryRepository.save(d);
            return false;
        }
        if (existing.isPresent()) {
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
     * Inserts cape history for this player.
     * 
     * @param playerId the player id
     * @param capeId the cape id
     * @param now the timestamp
     * @param updateLastUsed whether to update the last used timestamp
     * @return true if a new entry was inserted, false if an existing entry was updated
     */
    private boolean insertCapeHistory(UUID playerId, UUID capeId, Date now, boolean updateLastUsed) {
        Optional<CapeHistoryDocument> existing = capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeId);
        if (existing.isPresent() && updateLastUsed) {
            CapeHistoryDocument d = existing.get();
            d.setLastUsed(now);
            capeHistoryRepository.save(d);
            return false;
        }
        if (existing.isPresent()) {
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
     * 
     * @param player the player
     * @param document the player document
     * @param token the mojang profile token
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        UUID playerId = document.getId();
        String currentUsername = document.getUsername();
        UUID currentSkinId = document.getSkin() != null ? document.getSkin().getId() : null;
        UUID currentCapeId = document.getCape() != null ? document.getCape().getId() : null;

        applyProfileToPlayer(playerId, currentUsername, currentSkinId, currentCapeId, token, document, null);

        // Reload from manager to get updated doc and sync to Player entity
        playerManager.getByUuid(playerId).ifPresent(doc -> {
            player.setUsername(doc.getUsername());
            player.setSkin(doc.getSkin() != null ? skinManager.getById(doc.getSkin().getId()).map(skinService::fromDocument).orElse(null) : null);
            player.setCape(doc.getCape() != null ? capeManager.getById(doc.getCape().getId()).map(capeService::fromDocument).orElse(null) : null);
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
