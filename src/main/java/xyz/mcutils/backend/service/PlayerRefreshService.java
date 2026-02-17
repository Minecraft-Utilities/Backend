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
    private static final int REFRESH_WORKER_THREADS = 50;
    private static final int HISTORY_OR_CHUNK = 1000;

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
        ChunkBatch batch = new ChunkBatch();
        List<Future<?>> futures = new ArrayList<>();
        for (PlayerRefreshRow row : rows) {
            Future<?> future = Main.EXECUTOR.submit(() -> {
                refreshConcurrencyLimit.acquireUninterruptibly();
                try {
                    MojangProfileToken token = this.mojangService.getProfile(row.getId().toString());
                    if (token == null) return;
                    Update update = this.updatePlayerFromToken(row, token, batch);
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
        Date now = new Date();
        if (!updates.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, PlayerDocument.class);
            for (Tuple<UUID, Update> pair : updates) {
                bulkOps.updateOne(Query.query(Criteria.where("_id").is(pair.left())), pair.right());
            }
            bulkOps.execute();
        }
        Set<UUID> skinIdsToIncrement = new HashSet<>();
        Set<UUID> capeIdsToIncrement = new HashSet<>();
        flushHistory(batch, now, skinIdsToIncrement, capeIdsToIncrement);
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
     * Builds an Update for the player from the Mojang profile token.
     * When {@code batch} is non-null (chunk refresh), only resolves skin/cape and records history intents in batch; no history DB calls.
     * When {@code batch} is null (single-player refresh), runs history find/save inline.
     *
     * @param row   current player state from projection
     * @param token Mojang profile token
     * @param batch optional; when set, history is flushed later via {@link #flushHistory}
     * @return Update to apply to the players collection
     */
    @SneakyThrows
    public Update updatePlayerFromToken(PlayerRefreshRow row, MojangProfileToken token, ChunkBatch batch) {
        UUID playerId = row.getId();
        String newUsername = token.getName();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        if (batch != null) {
            if (row.getUsername() != null) batch.ensureUsername.add(new Tuple<>(playerId, row.getUsername()));
            if (!Objects.equals(row.getUsername(), newUsername)) batch.newNameEntries.add(new Tuple<>(playerId, newUsername));
            Skin skin = skinAndCape.left() != null ? skinService.getOrCreateSkinByTextureId(skinAndCape.left(), playerId) : null;
            UUID skinToSet = skin != null ? skin.getUuid() : row.getSkin();
            if (skinToSet != null) batch.ensureSkin.add(new Tuple<>(playerId, skinToSet));
            VanillaCape cape = skinAndCape.right() != null ? capeService.getCapeByTextureId(skinAndCape.right().getTextureId()) : null;
            UUID capeToSet = cape != null ? cape.getUuid() : row.getCape();
            if (capeToSet != null) batch.ensureCape.add(new Tuple<>(playerId, capeToSet));

            Update update = new Update();
            update.set("username", newUsername);
            update.set("skin", skinToSet != null ? SkinDocument.builder().id(skinToSet).build() : null);
            update.set("cape", capeToSet != null ? CapeDocument.builder().id(capeToSet).build() : null);
            update.set("legacyAccount", token.isLegacy());
            update.set("lastUpdated", new Date());
            OptifineCape.capeExists(token.getName(), webRequest)
                    .thenAccept(has -> mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(playerId)),
                            new Update().set("hasOptifineCape", has),
                            PlayerDocument.class
                    ));
            return update;
        }

        Date now = new Date();
        Update update = new Update();
        update.set("username", processUsernameHistory(playerId, row.getUsername(), newUsername, now));
        update.set("skin", processSkinHistoryAndResolve(playerId, row.getSkin(), skinAndCape.left(), now, null));
        update.set("cape", processCapeHistoryAndResolve(playerId, row.getCape(), skinAndCape.right(), now, null));
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

    /**
     * Flushes the history for a chunk of players.
     *
     * @param batch the chunk batch
     * @param now the current date
     * @param skinIdsToIncrement the set of skin IDs to increment
     * @param capeIdsToIncrement the set of cape IDs to increment
     */
    private void flushHistory(ChunkBatch batch, Date now, Set<UUID> skinIdsToIncrement, Set<UUID> capeIdsToIncrement) {
        String usernameColl = mongoTemplate.getCollectionName(UsernameHistoryDocument.class);
        String skinColl = mongoTemplate.getCollectionName(SkinHistoryDocument.class);
        String capeColl = mongoTemplate.getCollectionName(CapeHistoryDocument.class);

        List<Tuple<UUID, String>> ensureUserList = new ArrayList<>(batch.ensureUsername);
        Set<Tuple<UUID, String>> usernameExists = new HashSet<>();
        List<UsernameHistoryDocument> usernameToUpdate = new ArrayList<>();
        for (int i = 0; i < ensureUserList.size(); i += HISTORY_OR_CHUNK) {
            List<Tuple<UUID, String>> chunk = ensureUserList.subList(i, Math.min(i + HISTORY_OR_CHUNK, ensureUserList.size()));
            Criteria[] or = chunk.stream().map(t -> Criteria.where("playerId").is(t.left()).and("username").is(t.right())).toArray(Criteria[]::new);
            List<UsernameHistoryDocument> found = mongoTemplate.find(Query.query(new Criteria().orOperator(or)), UsernameHistoryDocument.class, usernameColl);
            for (UsernameHistoryDocument d : found) {
                usernameExists.add(new Tuple<>(d.getPlayerId(), d.getUsername()));
                usernameToUpdate.add(d);
            }
        }
        List<UsernameHistoryDocument> usernameInserts = new ArrayList<>();
        for (Tuple<UUID, String> t : ensureUserList) {
            if (!usernameExists.contains(t)) usernameInserts.add(UsernameHistoryDocument.builder().id(UUID.randomUUID()).playerId(t.left()).username(t.right()).timestamp(now).build());
        }
        for (Tuple<UUID, String> t : batch.newNameEntries) usernameInserts.add(UsernameHistoryDocument.builder().id(UUID.randomUUID()).playerId(t.left()).username(t.right()).timestamp(now).build());
        if (!usernameInserts.isEmpty()) mongoTemplate.insert(usernameInserts, UsernameHistoryDocument.class);
        if (!usernameToUpdate.isEmpty()) {
            BulkOperations usernameBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, UsernameHistoryDocument.class);
            for (UsernameHistoryDocument d : usernameToUpdate) usernameBulk.updateOne(Query.query(Criteria.where("_id").is(d.getId())), new Update().set("timestamp", now));
            usernameBulk.execute();
        }

        List<Tuple<UUID, UUID>> ensureSkinList = new ArrayList<>(batch.ensureSkin);
        Set<Tuple<UUID, UUID>> skinExists = new HashSet<>();
        List<SkinHistoryDocument> skinToUpdate = new ArrayList<>();
        for (int i = 0; i < ensureSkinList.size(); i += HISTORY_OR_CHUNK) {
            List<Tuple<UUID, UUID>> chunk = ensureSkinList.subList(i, Math.min(i + HISTORY_OR_CHUNK, ensureSkinList.size()));
            Criteria[] or = chunk.stream().map(t -> Criteria.where("playerId").is(t.left()).and("skin").is(t.right())).toArray(Criteria[]::new);
            List<SkinHistoryDocument> found = mongoTemplate.find(Query.query(new Criteria().orOperator(or)), SkinHistoryDocument.class, skinColl);
            for (SkinHistoryDocument d : found) {
                UUID sid = d.getSkin() != null ? d.getSkin().getId() : null;
                skinExists.add(new Tuple<>(d.getPlayerId(), sid));
                skinToUpdate.add(d);
            }
        }
        List<SkinHistoryDocument> skinInserts = new ArrayList<>();
        for (Tuple<UUID, UUID> t : ensureSkinList) {
            if (!skinExists.contains(t)) {
                skinInserts.add(SkinHistoryDocument.builder().id(UUID.randomUUID()).playerId(t.left()).skin(SkinDocument.builder().id(t.right()).build()).lastUsed(now).timestamp(now).build());
                skinIdsToIncrement.add(t.right());
            }
        }
        if (!skinInserts.isEmpty()) mongoTemplate.insert(skinInserts, SkinHistoryDocument.class);
        if (!skinToUpdate.isEmpty()) {
            BulkOperations skinBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, SkinHistoryDocument.class);
            for (SkinHistoryDocument d : skinToUpdate) skinBulk.updateOne(Query.query(Criteria.where("_id").is(d.getId())), new Update().set("lastUsed", now));
            skinBulk.execute();
        }

        List<Tuple<UUID, UUID>> ensureCapeList = new ArrayList<>(batch.ensureCape);
        Set<Tuple<UUID, UUID>> capeExists = new HashSet<>();
        List<CapeHistoryDocument> capeToUpdate = new ArrayList<>();
        for (int i = 0; i < ensureCapeList.size(); i += HISTORY_OR_CHUNK) {
            List<Tuple<UUID, UUID>> chunk = ensureCapeList.subList(i, Math.min(i + HISTORY_OR_CHUNK, ensureCapeList.size()));
            Criteria[] or = chunk.stream().map(t -> Criteria.where("playerId").is(t.left()).and("cape").is(t.right())).toArray(Criteria[]::new);
            List<CapeHistoryDocument> found = mongoTemplate.find(Query.query(new Criteria().orOperator(or)), CapeHistoryDocument.class, capeColl);
            for (CapeHistoryDocument d : found) {
                UUID cid = d.getCape() != null ? d.getCape().getId() : null;
                capeExists.add(new Tuple<>(d.getPlayerId(), cid));
                capeToUpdate.add(d);
            }
        }
        List<CapeHistoryDocument> capeInserts = new ArrayList<>();
        for (Tuple<UUID, UUID> t : ensureCapeList) {
            if (!capeExists.contains(t)) {
                capeInserts.add(CapeHistoryDocument.builder().id(UUID.randomUUID()).playerId(t.left()).cape(CapeDocument.builder().id(t.right()).build()).lastUsed(now).timestamp(now).build());
                capeIdsToIncrement.add(t.right());
            }
        }
        if (!capeInserts.isEmpty()) mongoTemplate.insert(capeInserts, CapeHistoryDocument.class);
        if (!capeToUpdate.isEmpty()) {
            BulkOperations capeBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, CapeHistoryDocument.class);
            for (CapeHistoryDocument d : capeToUpdate) capeBulk.updateOne(Query.query(Criteria.where("_id").is(d.getId())), new Update().set("lastUsed", now));
            capeBulk.execute();
        }
    }

    /**
     * Mutable state for one refresh chunk: history (playerId, key) pairs to ensure exist, and (playerId, newName) when name changed.
     * Workers add to these; after the chunk we batch-find, then bulk insert/update.
     */
    private static final class ChunkBatch {
        final Set<Tuple<UUID, String>> ensureUsername = Collections.synchronizedSet(new HashSet<>());
        final List<Tuple<UUID, String>> newNameEntries = Collections.synchronizedList(new ArrayList<>());
        final Set<Tuple<UUID, UUID>> ensureSkin = Collections.synchronizedSet(new HashSet<>());
        final Set<Tuple<UUID, UUID>> ensureCape = Collections.synchronizedSet(new HashSet<>());
    }
}
