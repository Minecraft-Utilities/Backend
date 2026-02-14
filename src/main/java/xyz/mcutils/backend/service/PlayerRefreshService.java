package xyz.mcutils.backend.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final Duration MIN_TIME_BETWEEN_UPDATES = Duration.ofHours(1);
    private static final int REFRESH_WORKER_THREADS = 25;

    private final Semaphore refreshConcurrencyLimit = new Semaphore(REFRESH_WORKER_THREADS);
    
    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final WebRequest webRequest;
    private final MongoTemplate mongoTemplate;


    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService,
                            SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository,
                            UsernameHistoryRepository usernameHistoryRepository, WebRequest webRequest, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.webRequest = webRequest;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    Date cutoff = Date.from(Instant.now().minus(MIN_TIME_BETWEEN_UPDATES));
                    List<PlayerRefreshRow> rows = findRefreshChunk(cutoff, PageRequest.of(0, 20_000));
                    if (rows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(60).toMillis());
                        continue;
                    }
                    this.processRefreshChunk(rows);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Loads a chunk of players for refresh using a projection (id, username, skin, cape, lastUpdated, legacyAccount only).
     */
    private List<PlayerRefreshRow> findRefreshChunk(Date lastUpdatedBefore, Pageable pageable) {
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
     * Refreshes a chunk of players from the Mojang API using projection and partial updates.
     *
     * @param rows the chunk of player rows to refresh (projection only)
     */
    @SneakyThrows
    private void processRefreshChunk(List<PlayerRefreshRow> rows) {
        List<Tuple<UUID, Update>> updates = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (PlayerRefreshRow row : rows) {
            Future<?> future = Main.EXECUTOR.submit(() -> {
                refreshConcurrencyLimit.acquireUninterruptibly();
                try {
                    MojangProfileToken token = this.mojangService.getProfile(row.getId().toString());
                    if (token == null) {
                        return;
                    }
                    Update update = this.updatePlayerFromToken(row, token);
                    if (update != null) {
                        updates.add(new Tuple<>(row.getId(), update));
                    }
                } finally {
                    refreshConcurrencyLimit.release();
                }
            });
            futures.add(future);
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                log.warn("Player refresh task failed", e.getCause());
            }
        }
        if (!updates.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, PlayerDocument.class);
            for (Tuple<UUID, Update> pair : updates) {
                bulkOps.updateOne(
                        Query.query(Criteria.where("_id").is(pair.left())),
                        pair.right()
                );
            }
            bulkOps.execute();
        }
    }

    /**
     * Updates player state from the Mojang profile token (document-only path).
     * Performs history writes and returns an {@link Update} to apply to the players collection.
     *
     * @param row   current player state from projection
     * @param token Mojang profile token
     * @return Update to apply, or null if nothing to update
     */
    @SneakyThrows
    public Update updatePlayerFromToken(PlayerRefreshRow row, MojangProfileToken token) {
        Date now = new Date();
        UUID playerId = row.getId();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        Update update = new Update();

        String newUsername = processUsernameHistory(playerId, row.getUsername(), token.getName(), now);
        update.set("username", newUsername);

        SkinDocument newSkinRef = processSkinHistoryAndResolve(playerId, row.getSkin(), skinAndCape.left(), now);
        update.set("skin", newSkinRef);

        CapeDocument newCapeRef = processCapeHistoryAndResolve(playerId, row.getCape(), skinAndCape.right(), now);
        update.set("cape", newCapeRef);

        update.set("legacyAccount", token.isLegacy());
        update.set("lastUpdated", now);

        OptifineCape.capeExists(token.getName(), webRequest)
                .thenAccept(has -> mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(playerId)),
                        new Update().set("hasOptifineCape", has),
                        PlayerDocument.class
                ));

        return update;
    }

    /**
     * Applies username history and returns the username to set (new name from profile).
     */
    private String processUsernameHistory(UUID playerId, String currentUsername, String newName, Date now) {
        if (currentUsername != null) {
            this.usernameHistoryRepository.findFirstByPlayerIdAndUsername(playerId, currentUsername)
                    .map(existing -> {
                        existing.setTimestamp(now);
                        this.usernameHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .username(currentUsername)
                                .timestamp(now)
                                .build());
                        return true;
                    });
        }
        if (!Objects.equals(currentUsername, newName)) {
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(newName)
                    .timestamp(now)
                    .build());
        }
        return newName;
    }

    /**
     * Applies skin history and resolves new skin from token. Returns the skin ref to set (or null if no skin).
     */
    private SkinDocument processSkinHistoryAndResolve(UUID playerId, UUID currentSkinId, SkinTextureToken skinToken, Date now) {
        UUID newSkinId = null;
        if (skinToken != null) {
            Skin newSkin = this.skinService.getOrCreateSkinByTextureId(skinToken, playerId);
            newSkinId = newSkin != null ? newSkin.getUuid() : null;
        }
        boolean skinChanged = !Objects.equals(currentSkinId, newSkinId);
        UUID skinToSet = skinChanged ? newSkinId : currentSkinId;

        if (skinToSet != null) {
            boolean currentNotInHistory = this.skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinToSet)
                    .map(existing -> {
                        existing.setLastUsed(now);
                        this.skinHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.skinHistoryRepository.save(SkinHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .skin(SkinDocument.builder().id(skinToSet).build())
                                .lastUsed(now)
                                .timestamp(now)
                                .build());
                        return true;
                    });
            if (currentNotInHistory) {
                this.skinService.incrementAccountsUsed(skinToSet);
            }
        }

        return skinToSet != null ? SkinDocument.builder().id(skinToSet).build() : null;
    }

    /**
     * Applies cape history and resolves new cape from token. Returns the cape ref to set (or null if no cape).
     */
    private CapeDocument processCapeHistoryAndResolve(UUID playerId, UUID currentCapeId, CapeTextureToken capeToken, Date now) {
        UUID newCapeId = null;
        if (capeToken != null) {
            VanillaCape newCape = this.capeService.getCapeByTextureId(capeToken.getTextureId());
            newCapeId = newCape != null ? newCape.getUuid() : null;
        }
        boolean capeChanged = !Objects.equals(currentCapeId, newCapeId);
        UUID capeToSet = capeChanged ? newCapeId : currentCapeId;

        if (capeToSet != null) {
            boolean currentNotInHistory = this.capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeToSet)
                    .map(existing -> {
                        existing.setLastUsed(now);
                        this.capeHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.capeHistoryRepository.save(CapeHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .cape(CapeDocument.builder().id(capeToSet).build())
                                .lastUsed(now)
                                .timestamp(now)
                                .build());
                        return true;
                    });
            if (currentNotInHistory) {
                this.capeService.incrementAccountsOwned(capeToSet);
            }
        }

        return capeToSet != null ? CapeDocument.builder().id(capeToSet).build() : null;
    }

    /**
     * Updates the player with their new data from the {@link MojangProfileToken}
     *
     * @param player   the player to update
     * @param document the player's document
     * @param token    the player's {@link MojangProfileToken} token
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        Date now = new Date();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        UUID playerId = document.getId();

        String username = processUsernameHistory(playerId, document.getUsername(), token.getName(), now);
        document.setUsername(username);
        player.setUsername(username);

        UUID currentSkinId = document.getSkin() != null ? document.getSkin().getId() : null;
        SkinDocument newSkinRef = processSkinHistoryAndResolve(playerId, currentSkinId, skinAndCape.left(), now);
        document.setSkin(newSkinRef);
        player.setSkin(newSkinRef != null ? this.skinService.fromDocument(newSkinRef) : null);

        UUID currentCapeId = document.getCape() != null ? document.getCape().getId() : null;
        CapeDocument newCapeRef = processCapeHistoryAndResolve(playerId, currentCapeId, skinAndCape.right(), now);
        document.setCape(newCapeRef);
        player.setCape(newCapeRef != null ? this.capeService.fromDocument(newCapeRef) : null);

        if (player.isLegacyAccount() != token.isLegacy()) {
            document.setLegacyAccount(token.isLegacy());
            player.setLegacyAccount(token.isLegacy());
        }

        OptifineCape.capeExists(token.getName(), webRequest)
                .thenAccept(has -> mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(playerId)),
                        new Update().set("hasOptifineCape", has),
                        PlayerDocument.class
                ));

        document.setLastUpdated(now);
        player.setLastUpdated(now);
    }
}
