package xyz.mcutils.backend.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import xyz.mcutils.backend.Main;
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
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final Duration MIN_TIME_BETWEEN_UPDATES = Duration.ofHours(1);
    private static final int REFRESH_CHUNK_SIZE = 10000;

    private final Semaphore refreshConcurrencyLimit = new Semaphore(50);

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final PlayerRepository playerRepository;
    private final MongoTemplate mongoTemplate;

    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService,
                                PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager,
                                SkinHistoryRepository skinHistoryRepository,
                                CapeHistoryRepository capeHistoryRepository,
                                UsernameHistoryRepository usernameHistoryRepository,
                                PlayerRepository playerRepository, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.playerRepository = playerRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    Date cutoff = Date.from(Instant.now().minus(MIN_TIME_BETWEEN_UPDATES));
                    List<PlayerRefreshRow> rows = findRefreshChunk(cutoff, PageRequest.of(0, REFRESH_CHUNK_SIZE));
                    if (rows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10).toMillis());
                        continue;
                    }
                    List<Future<?>> futures = new ArrayList<>();
                    for (PlayerRefreshRow row : rows) {
                        futures.add(Main.EXECUTOR.submit(() -> {
                            refreshConcurrencyLimit.acquireUninterruptibly();
                            try {
                                MojangProfileToken token = this.mojangService.getProfile(row.getId().toString());
                                if (token == null) {
                                    return;
                                }
                                applyProfileToPlayer(row.getId(), row.getUsername(), row.getSkin(), row.getCape(), token);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private List<PlayerRefreshRow> findRefreshChunk(Date lastUpdatedBefore, PageRequest pageable) {
        Query query = Query.query(Criteria.where("lastUpdated").lt(lastUpdatedBefore))
                .with(Sort.by(Sort.Direction.ASC, "lastUpdated"))
                .limit(pageable.getPageSize())
                .skip(pageable.getOffset());
        query.fields()
                .include("username")
                .include("skin")
                .include("cape")
                .include("lastUpdated")
                .include("legacyAccount");
        return mongoTemplate.find(query, PlayerRefreshRow.class, "players");
    }

    /**
     * Applies Mojang profile to the player: resolves skin/cape via managers, writes history, updates cached document and marks dirty.
     */
    private void applyProfileToPlayer(UUID playerId, String currentUsername, UUID currentSkinId, UUID currentCapeId,
                                      MojangProfileToken token) {
        Date now = new Date();
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

        // Username history
        ensureUsernameHistory(playerId, currentUsername, token.getName(), now);

        // Skin history + increment when new
        if (newSkinId != null && ensureSkinHistory(playerId, newSkinId, now)) {
            skinManager.incrementAccountsUsed(newSkinId, 1);
        }

        // Cape history + increment when new
        if (newCapeId != null && ensureCapeHistory(playerId, newCapeId, now)) {
            capeManager.incrementAccountsOwned(newCapeId, 1);
        }

        // Update cached player document
        PlayerDocument doc = playerManager.getByUuid(playerId)
                .orElseGet(() -> playerRepository.findById(playerId).orElse(null));
        if (doc == null) {
            return;
        }
        doc.setUsername(token.getName());
        doc.setSkin(newSkinId != null ? SkinDocument.builder().id(newSkinId).build() : null);
        doc.setCape(newCapeId != null ? CapeDocument.builder().id(newCapeId).build() : null);
        doc.setLegacyAccount(token.isLegacy());
        doc.setLastUpdated(now);
        playerManager.markDirty(playerId);

        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
    }

    private void ensureUsernameHistory(UUID playerId, String currentUsername, String newName, Date now) {
        if (currentUsername != null) {
            usernameHistoryRepository.findFirstByPlayerIdAndUsername(playerId, currentUsername)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setTimestamp(now);
                                usernameHistoryRepository.save(existing);
                            },
                            () -> usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                                    .id(UUID.randomUUID())
                                    .playerId(playerId)
                                    .username(currentUsername)
                                    .timestamp(now)
                                    .build()));
        }
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
     * Ensures a skin history entry exists. Returns true if a new entry was inserted (caller should increment).
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
     * Ensures a cape history entry exists. Returns true if a new entry was inserted (caller should increment).
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

        applyProfileToPlayer(playerId, currentUsername, currentSkinId, currentCapeId, token);

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
}
