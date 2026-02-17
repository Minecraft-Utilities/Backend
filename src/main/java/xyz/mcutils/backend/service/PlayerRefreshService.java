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
    private static final int REFRESH_WORKER_THREADS = 75;

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
     * Finds a chunk of players to refresh.
     *
     * @param lastUpdatedBefore the date to find players updated before
     * @param pageable          the pageable to use
     * @return the chunk of player rows to refresh (projection only)
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
     * Processes a chunk of players from the Mojang API.
     *
     * @param rows the chunk of player rows to refresh (projection only)
     */
    @SneakyThrows
    private void processRefreshChunk(List<PlayerRefreshRow> rows) {
        List<Tuple<UUID, Update>> updates = Collections.synchronizedList(new ArrayList<>());
        Set<UUID> skinIdsToIncrement = Collections.synchronizedSet(new HashSet<>());
        Set<UUID> capeIdsToIncrement = Collections.synchronizedSet(new HashSet<>());
        List<Future<?>> futures = new ArrayList<>();
        for (PlayerRefreshRow row : rows) {
            Future<?> future = Main.EXECUTOR.submit(() -> {
                refreshConcurrencyLimit.acquireUninterruptibly();
                try {
                    MojangProfileToken token = this.mojangService.getProfile(row.getId().toString());
                    if (token == null) return;
                    Update update = this.updatePlayerFromToken(row, token, skinIdsToIncrement, capeIdsToIncrement);
                    if (update != null) updates.add(new Tuple<>(row.getId(), update));
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
                bulkOps.updateOne(Query.query(Criteria.where("_id").is(pair.left())), pair.right());
            }
            bulkOps.execute();
        }
        if (!skinIdsToIncrement.isEmpty()) {
            BulkOperations skinBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, SkinDocument.class);
            for (UUID id : skinIdsToIncrement) {
                skinBulk.updateOne(Query.query(Criteria.where("_id").is(id)), new Update().inc("accountsUsed", 1));
            }
            skinBulk.execute();
        }
        if (!capeIdsToIncrement.isEmpty()) {
            BulkOperations capeBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, CapeDocument.class);
            for (UUID id : capeIdsToIncrement) {
                capeBulk.updateOne(Query.query(Criteria.where("_id").is(id)), new Update().inc("accountsOwned", 1));
            }
            capeBulk.execute();
        }
    }

    /**
     * Updates the player with their new data from the {@link MojangProfileToken}
     *
     * @param row                   current player state from projection
     * @param token                 Mojang profile token
     * @param skinIdsToIncrement    optional set to collect skin IDs for bulk increment; null to increment immediately
     * @param capeIdsToIncrement    optional set to collect cape IDs for bulk increment; null to increment immediately
     * @return update to apply to the players collection, or null if nothing to update
     */
    @SneakyThrows
    public Update updatePlayerFromToken(PlayerRefreshRow row, MojangProfileToken token,
            Set<UUID> skinIdsToIncrement, Set<UUID> capeIdsToIncrement) {
        Date now = new Date();
        UUID playerId = row.getId();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        Update update = new Update();
        update.set("username", processUsernameHistory(playerId, row.getUsername(), token.getName(), now));
        SkinDocument newSkinRef = processSkinHistoryAndResolve(playerId, row.getSkin(), skinAndCape.left(), now, skinIdsToIncrement);
        update.set("skin", newSkinRef);
        CapeDocument newCapeRef = processCapeHistoryAndResolve(playerId, row.getCape(), skinAndCape.right(), now, capeIdsToIncrement);
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
     * Processes username history, ensuring an entry exists (update or insert) and returns the username to set (new name from profile).
     *
     * @param playerId            the player ID
     * @param currentUsername     the current username
     * @param newName             the new username from profile
     * @param now                 the current date
     * @return the username to set on the player
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
     * Processes skin history, ensuring an entry exists (update or insert) and returns the skin to set (new skin from profile).
     *
     * @param playerId            the player ID
     * @param currentSkinId       the current skin ID
     * @param skinToken           the skin token
     * @param now                 the current date
     * @param skinIdsToIncrement  optional set to collect skin IDs for bulk increment; null to increment immediately
     * @return the skin to set on the player
     */
    private SkinDocument processSkinHistoryAndResolve(UUID playerId, UUID currentSkinId, SkinTextureToken skinToken, Date now, Set<UUID> skinIdsToIncrement) {
        Skin skin = skinToken != null ? this.skinService.getOrCreateSkinByTextureId(skinToken, playerId) : null;
        UUID newSkinId = skin != null ? skin.getUuid() : null;
        UUID skinToSet = !Objects.equals(currentSkinId, newSkinId) ? newSkinId : currentSkinId;
        if (skinToSet == null) return null;

        boolean inserted = this.skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinToSet)
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
        if (inserted) {
            if (skinIdsToIncrement != null) skinIdsToIncrement.add(skinToSet);
            else this.skinService.incrementAccountsUsed(skinToSet);
        }
        return SkinDocument.builder().id(skinToSet).build();
    }
   
    /**
     * Processes cape history, ensuring an entry exists (update or insert) and returns the cape to set (new cape from profile).
     *
     * @param playerId            the player ID
     * @param currentCapeId       the current cape ID
     * @param capeToken           the cape token
     * @param now                 the current date
     * @param capeIdsToIncrement  optional set to collect cape IDs for bulk increment; null to increment immediately
     * @return the cape to set on the player
     */
    private CapeDocument processCapeHistoryAndResolve(UUID playerId, UUID currentCapeId, CapeTextureToken capeToken, Date now, Set<UUID> capeIdsToIncrement) {
        VanillaCape cape = capeToken != null ? this.capeService.getCapeByTextureId(capeToken.getTextureId()) : null;
        UUID newCapeId = cape != null ? cape.getUuid() : null;
        UUID capeToSet = !Objects.equals(currentCapeId, newCapeId) ? newCapeId : currentCapeId;
        if (capeToSet == null) return null;

        boolean inserted = this.capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeToSet)
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
        if (inserted) {
            if (capeIdsToIncrement != null) capeIdsToIncrement.add(capeToSet);
            else this.capeService.incrementAccountsOwned(capeToSet);
        }
        return CapeDocument.builder().id(capeToSet).build();
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
        SkinDocument newSkinRef = processSkinHistoryAndResolve(playerId, currentSkinId, skinAndCape.left(), now, null);
        document.setSkin(newSkinRef);
        player.setSkin(newSkinRef != null ? this.skinService.getSkinById(newSkinRef.getId()) : null);

        UUID currentCapeId = document.getCape() != null ? document.getCape().getId() : null;
        CapeDocument newCapeRef = processCapeHistoryAndResolve(playerId, currentCapeId, skinAndCape.right(), now, null);
        document.setCape(newCapeRef);
        player.setCape(newCapeRef != null ? this.capeService.getCapeById(newCapeRef.getId()) : null);

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
