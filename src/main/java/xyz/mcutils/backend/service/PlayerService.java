package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.player.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.dto.PlayerCreateSubmission;
import xyz.mcutils.backend.model.dto.response.PlayerSearchEntry;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.skin.SkinManager;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class PlayerService {
    private static final Duration PLAYER_UPDATE_INTERVAL = Duration.ofHours(3);
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;

    public static PlayerService INSTANCE;
    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final PlayerHistoryService playerHistoryService;
    private final MongoTemplate mongoTemplate;
    private final CoalescingLoader<String, Player> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerManager playerManager,
                         SkinManager skinManager, CapeManager capeManager, PlayerHistoryService playerHistoryService, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
        this.playerHistoryService = playerHistoryService;
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    /**
     * Get a player from the database or from the Mojang API.
     *
     * @param query the query to look up the player by (UUID or username)
     * @return the player
     */
    public Player getPlayer(String query) {
        return playerLoader.get(query, () -> {
            boolean isUsername = query.length() <= 16;
            Optional<PlayerDocument> playerDocument = isUsername ? this.playerManager.getByUsername(query) : this.playerManager.getByUuid(UUIDUtils.parseUuid(query));
            UUID playerUuid;
            if (playerDocument.isEmpty()) {
                MojangUsernameToUuidToken mojangUsernameToUuid = this.mojangService.getUuidFromUsername(query);
                if (mojangUsernameToUuid == null) {
                    throw new NotFoundException("Player with username '%s' was not found".formatted(query));
                }
                playerUuid = UUIDUtils.addDashes(mojangUsernameToUuid.uuid());
            }
            else {
                playerUuid = playerDocument.get().getId();
            }

            UUID finalPlayerUuid = playerUuid;
            return playerDocument.map(document -> {
                Player player = fromDocument(document);
                if (document.getLastUpdated().toInstant().isBefore(Instant.now().minus(PLAYER_UPDATE_INTERVAL))) {
                    MojangProfileToken token = mojangService.getProfile(finalPlayerUuid.toString());
                    if (token == null) {
                        throw new NotFoundException("Player with uuid '%s' was not found".formatted(finalPlayerUuid));
                    }
                    this.updatePlayer(player, document, token);
                }
                return player;
            }).orElseGet(() -> {
                try {
                    MojangProfileToken token = mojangService.getProfile(finalPlayerUuid.toString());
                    if (token == null) {
                        throw new NotFoundException("Player with uuid '%s' was not found".formatted(finalPlayerUuid));
                    }
                    return this.createPlayer(token);
                } catch (RateLimitException exception) {
                    throw new MojangAPIRateLimitException();
                }
            });
        });
    }

    /**
     * Creates a new player from their {@link MojangProfileToken}.
     * Delegates to {@link #createPlayers(List)} with a single submission, then loads and returns the created player.
     *
     * @param token the token for the player
     * @return the created player
     */
    public Player createPlayer(MojangProfileToken token) {
        createPlayers(List.of(new PlayerCreateSubmission(token)));
        UUID playerUuid = UUIDUtils.addDashes(token.getId());
        PlayerDocument document = this.playerManager.getByUuid(playerUuid).orElseThrow(() -> new IllegalStateException("Player not found after create: " + playerUuid));
        return fromDocument(document);
    }

    /**
     * Creates multiple players in one batch (bulk inserts and bulk increments).
     * Use this for submit-queue or any batch flow; use {@link #createPlayer(MojangProfileToken)} for a single player.
     * If {@link PlayerCreateSubmission#submittedBy()} is non-null, increments that player's submittedUuids once per occurrence.
     * Empty list is a no-op.
     *
     * @param submissions list of profile + optional submitter (empty list is a no-op)
     */
    public void createPlayers(List<PlayerCreateSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return;
        }
        Date now = new Date();
        List<PlayerDocument> playerDocuments = new ArrayList<>();
        List<SkinHistoryDocument> skinHistoryDocuments = new ArrayList<>();
        List<UsernameHistoryDocument> usernameHistoryDocuments = new ArrayList<>();
        List<CapeHistoryDocument> capeHistoryDocuments = new ArrayList<>();
        Map<UUID, Long> skinCounts = new HashMap<>();
        Map<UUID, Long> capeCounts = new HashMap<>();
        Map<UUID, Long> submitterCounts = new HashMap<>();

        for (PlayerCreateSubmission submission : submissions) {
            MojangProfileToken token = submission.profile();
            UUID playerUuid = UUIDUtils.addDashes(token.getId());
            Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

            Skin skin = null;
            if (skinAndCape != null && skinAndCape.left() != null) {
                skin = this.skinService.getOrCreateSkinByTextureId(skinAndCape.left(), playerUuid);
            }
            VanillaCape cape = null;
            if (skinAndCape != null && skinAndCape.right() != null) {
                cape = this.capeService.getCapeByTextureId(skinAndCape.right().getTextureId());
            }

            UUID skinUuid = skin != null ? skin.getUuid() : null;
            UUID capeUuid = cape != null ? cape.getUuid() : null;

            playerDocuments.add(PlayerDocument.builder().id(playerUuid).username(token.getName()).legacyAccount(token.isLegacy()).skinId(skinUuid).capeId(capeUuid).lastUpdated(now).firstSeen(now).build());

            skinHistoryDocuments.add(SkinHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerUuid).skin(skinUuid != null ? SkinDocument.builder().id(skinUuid).build() : null).lastUsed(now).timestamp(now).build());

            usernameHistoryDocuments.add(UsernameHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerUuid).username(token.getName()).timestamp(now).build());

            if (capeUuid != null) {
                capeHistoryDocuments.add(CapeHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerUuid).cape(CapeDocument.builder().id(capeUuid).build()).lastUsed(now).timestamp(now).build());
            }

            if (skinUuid != null) {
                skinCounts.merge(skinUuid, 1L, Long::sum);
            }
            if (capeUuid != null) {
                capeCounts.merge(capeUuid, 1L, Long::sum);
            }
            UUID submittedBy = submission.submittedBy();
            if (submittedBy != null) {
                submitterCounts.merge(submittedBy, 1L, Long::sum);
            }
        }

        StatisticsService.addTrackedPlayerCount(submissions.size());

        MongoUtils.bulkInsertUnordered(mongoTemplate, playerDocuments, PlayerDocument.class);
        MongoUtils.bulkInsertUnordered(mongoTemplate, skinHistoryDocuments, SkinHistoryDocument.class);
        MongoUtils.bulkInsertUnordered(mongoTemplate, usernameHistoryDocuments, UsernameHistoryDocument.class);
        MongoUtils.bulkInsertUnordered(mongoTemplate, capeHistoryDocuments, CapeHistoryDocument.class);

        for (PlayerDocument doc : playerDocuments) {
            playerManager.put(doc);
        }
        for (Map.Entry<UUID, Long> e : skinCounts.entrySet()) {
            skinManager.incrementAccountsUsed(e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Long> e : capeCounts.entrySet()) {
            capeManager.incrementAccountsOwned(e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Long> e : submitterCounts.entrySet()) {
            playerManager.incrementSubmittedUuids(e.getKey(), e.getValue());
        }

        // Shared ensure-history path: guarantee current state is in history (fixes any partial insert or legacy gaps)
        for (PlayerDocument doc : playerDocuments) {
            playerHistoryService.ensureUsernameInHistory(doc.getId(), doc.getUsername(), now);
            if (doc.getSkinId() != null) {
                playerHistoryService.ensureSkinInHistory(doc.getId(), doc.getSkinId(), now);
            }
            if (doc.getCapeId() != null) {
                playerHistoryService.ensureCapeInHistory(doc.getId(), doc.getCapeId(), now);
            }
        }

        log.debug("Bulk created {} players", submissions.size());
    }

    /**
     * Returns which of the given IDs exist in the database. Checks cache first so IDs already in PlayerManager
     * avoid a DB read; only queries Mongo for the rest.
     *
     * @param ids the uuids to check
     * @return set of ids that exist
     */
    public Set<UUID> getExistingPlayerIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> existing = new HashSet<>();
        List<UUID> toQuery = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            if (this.playerManager.isCached(id)) {
                existing.add(id);
            }
            else {
                toQuery.add(id);
            }
        }
        if (toQuery.isEmpty()) {
            return Set.copyOf(existing);
        }
        Set<UUID> toQueryUnique = new HashSet<>(toQuery);
        Query query = Query.query(Criteria.where("_id").in(toQueryUnique));
        List<Document> foundDocs = MongoUtils.findWithFields(this.mongoTemplate, query, PlayerDocument.class, "_id");
        for (Document doc : foundDocs) {
            UUID id = doc.get("_id", UUID.class);
            if (id != null) {
                existing.add(id);
            }
        }
        return Set.copyOf(existing);
    }

    /**
     * Search for players whose username starts with the given query, case-insensitive.
     * Uses two queries: one for player docs (id, username, skin), one batch load for all referenced skins.
     *
     * @param query the prefix to match (e.g. "steve" matches "Steve", "STEVE")
     * @return list of matching players with skin, up to {@value #MAX_PLAYER_SEARCH_RESULTS}
     */
    public List<PlayerSearchEntry> searchPlayers(String query) {
        String prefixEnd = query.isEmpty() ? "\uFFFF" : query.charAt(query.length() - 1) == Character.MAX_VALUE ? query + "\uFFFF" : query.substring(0, query.length() - 1) + (char) (query.charAt(query.length() - 1) + 1);
        Query q = Query.query(Criteria.where("username").gte(query).lt(prefixEnd)).collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary())).withHint("username_case_insensitive").with(PageRequest.of(0, MAX_PLAYER_SEARCH_RESULTS));
        List<Document> docs = MongoUtils.findWithFields(mongoTemplate, q, PlayerDocument.class, "_id", "username", "skin");
        Set<UUID> skinIds = new HashSet<>();
        for (Document d : docs) {
            UUID sid = d.get("skin", UUID.class);
            if (sid != null) {
                skinIds.add(sid);
            }
        }
        Map<UUID, Skin> skinById = new HashMap<>();
        for (var e : skinManager.getByIds(skinIds).entrySet()) {
            skinById.put(e.getKey(), skinService.fromDocument(e.getValue()));
        }
        return docs.stream().map(d -> new PlayerSearchEntry(d.get("_id", UUID.class), d.getString("username"), d.get("skin", UUID.class) != null ? skinById.get(d.get("skin", UUID.class)) : null)).toList();
    }

    /**
     * Gets the number of tracked players.
     *
     * @return the number of tracked players
     */
    public long getTrackedPlayerCount() {
        return this.mongoTemplate.estimatedCount(PlayerDocument.class);
    }

    /**
     * Builds a {@link Player} domain object from an already-loaded {@link PlayerDocument}.
     * History is loaded from the history repositories by player id.
     *
     * @param document the player document (e.g. from a batch query or findById)
     * @return the player domain object
     */
    public Player fromDocument(PlayerDocument document) {
        UUID playerId = document.getId();
        List<SkinHistoryDocument> skinHistoryDocs = playerManager.getPlayerSkinHistory(playerId);
        List<CapeHistoryDocument> capeHistoryDocs = playerManager.getPlayerCapeHistory(playerId);
        List<UsernameHistoryDocument> usernameHistoryDocs = playerManager.getPlayerUsernameHistory(playerId);

        Set<UUID> skinIds = new HashSet<>();
        if (document.getSkinId() != null) {
            skinIds.add(document.getSkinId());
        }
        for (SkinHistoryDocument entry : skinHistoryDocs) {
            if (entry.getSkin() != null && entry.getSkin().getId() != null) {
                skinIds.add(entry.getSkin().getId());
            }
        }
        Set<UUID> capeIds = new HashSet<>();
        if (document.getCapeId() != null) {
            capeIds.add(document.getCapeId());
        }
        for (CapeHistoryDocument entry : capeHistoryDocs) {
            if (entry.getCape() != null && entry.getCape().getId() != null) {
                capeIds.add(entry.getCape().getId());
            }
        }
        Map<UUID, SkinDocument> skinDocById = skinManager.getByIds(skinIds);
        Map<UUID, CapeDocument> capeDocById = capeManager.getByIds(capeIds);

        Skin skin = null;
        if (document.getSkinId() != null) {
            SkinDocument sd = skinDocById.get(document.getSkinId());
            skin = sd != null ? skinService.fromDocument(sd) : null;
        }

        Set<Skin> skinHistory = null;
        if (!skinHistoryDocs.isEmpty()) {
            skinHistory = new HashSet<>();
            for (SkinHistoryDocument entry : skinHistoryDocs) {
                if (entry.getSkin() != null && entry.getSkin().getId() != null) {
                    SkinDocument sd = skinDocById.get(entry.getSkin().getId());
                    if (sd != null) {
                        skinHistory.add(skinService.fromDocument(sd));
                    }
                }
            }
        }

        VanillaCape cape = null;
        if (document.getCapeId() != null) {
            CapeDocument cd = capeDocById.get(document.getCapeId());
            cape = cd != null ? capeService.fromDocument(cd) : null;
        }
        Set<VanillaCape> capeHistory = null;
        if (!capeHistoryDocs.isEmpty()) {
            capeHistory = new HashSet<>();
            for (CapeHistoryDocument entry : capeHistoryDocs) {
                if (entry.getCape() != null && entry.getCape().getId() != null) {
                    CapeDocument cd = capeDocById.get(entry.getCape().getId());
                    if (cd != null) {
                        capeHistory.add(capeService.fromDocument(cd));
                    }
                }
            }
        }

        Set<UsernameHistory> usernameHistory = null;
        if (!usernameHistoryDocs.isEmpty()) {
            usernameHistory = new HashSet<>();
            for (UsernameHistoryDocument entry : usernameHistoryDocs) {
                usernameHistory.add(new UsernameHistory(entry.getUsername(), entry.getTimestamp()));
            }
        }

        return new Player(document.getId(), document.getUsername(), document.isLegacyAccount(), skin, skinHistory, cape, capeHistory, usernameHistory, document.getLastUpdated(), document.getFirstSeen());
    }

    /**
     * Writes username, skin, and cape history entries for the player, incrementing usage counters on first-seen entries.
     */
    private void writeHistory(UUID playerId, String currentName, String newName, UUID currentSkinId, UUID currentCapeId, ResolvedAssets assets, Date now) {
        if (!newName.equals(currentName)) {
            playerHistoryService.ensureUsernameInHistory(playerId, newName, now);
        }
        if (assets.skinId() != null && !assets.skinId().equals(currentSkinId) && playerHistoryService.ensureSkinInHistory(playerId, assets.skinId(), now)) {
            skinManager.incrementAccountsUsed(assets.skinId(), 1);
        }
        if (assets.capeId() != null && !assets.capeId().equals(currentCapeId) && playerHistoryService.ensureCapeInHistory(playerId, assets.capeId(), now)) {
            capeManager.incrementAccountsOwned(assets.capeId(), 1);
        }
    }

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
                log.debug("Cape resolve failed for player {}; keeping current cape {}", playerId, currentCapeId, e);
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
    public void applyProfileToPlayer(UUID playerId, UUID currentSkinId, UUID currentCapeId, MojangProfileToken token, PlayerDocument doc, Date updatedAt) {
        ResolvedAssets assets = resolveAssets(playerId, currentSkinId, currentCapeId, token);
        writeHistory(playerId, doc.getUsername(), token.getName(), currentSkinId, currentCapeId, assets, updatedAt);
        patchDocument(doc, token.getName(), token.isLegacy(), assets, updatedAt);
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

        applyProfileToPlayer(playerId, currentSkinId, currentCapeId, token, document, new Date());

        // Reload from manager to get updated doc and sync to Player entity
        playerManager.getByUuid(playerId).ifPresent(updated -> {
            player.setUsername(updated.getUsername());
            player.setSkin(updated.getSkinId() != null ? skinManager.getById(updated.getSkinId()).map(skinService::fromDocument).orElse(null) : null);
            player.setCape(updated.getCapeId() != null ? capeManager.getById(updated.getCapeId()).map(capeService::fromDocument).orElse(null) : null);
            player.setLegacyAccount(updated.isLegacyAccount());
            player.setLastUpdated(updated.getLastUpdated());
        });
    }

    private record ResolvedAssets(UUID skinId, UUID capeId) {}
}
