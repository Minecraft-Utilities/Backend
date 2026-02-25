package xyz.mcutils.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.MongoUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.player.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.dto.PlayerCreateSubmission;
import xyz.mcutils.backend.model.dto.response.PlayerSearchEntry;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;
import xyz.mcutils.backend.model.persistence.mongo.CapeHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;
import xyz.mcutils.backend.model.persistence.mongo.SkinHistoryDocument;
import xyz.mcutils.backend.model.persistence.mongo.UsernameHistoryDocument;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;

@Service
@Slf4j
public class PlayerService {
    public static PlayerService INSTANCE;

    private static final Duration PLAYER_UPDATE_INTERVAL = Duration.ofHours(3);
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;

    @Value("${mc-utils.cache.player.enabled}")
    private boolean cacheEnabled;

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerRefreshService playerRefreshService;
    private final PlayerRepository playerRepository;
    private final MongoTemplate mongoTemplate;
    private final CoalescingLoader<String, Player> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerRefreshService playerRefreshService,
                         PlayerRepository playerRepository, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRefreshService = playerRefreshService;
        this.playerRepository = playerRepository;
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
            Optional<PlayerDocument> playerDocument = isUsername ? this.getPlayerByUsername(query) : this.playerRepository.findById(UUIDUtils.parseUuid(query));
            UUID playerUuid;
            if (playerDocument.isEmpty()) {
                MojangUsernameToUuidToken mojangUsernameToUuid = this.mojangService.getUuidFromUsername(query);
                if (mojangUsernameToUuid == null) {
                    throw new NotFoundException("Player with username '%s' was not found".formatted(query));
                }
                playerUuid = UUIDUtils.addDashes(mojangUsernameToUuid.uuid());
            } else {
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
                        this.playerRefreshService.updatePlayer(player, document, token);
                    }
                    return player;
                })
                .orElseGet(() -> {
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
        PlayerDocument document = this.playerRepository.findById(playerUuid)
                .orElseThrow(() -> new IllegalStateException("Player not found after create: " + playerUuid));
        return fromDocument(document);
    }

    /**
     * Creates multiple players in one batch (bulk inserts and bulk increments).
     * Use this for submit-queue or any batch flow; use {@link #createPlayer(MojangProfileToken)} for a single player.
     * In bulk, Optifine cape is not checked (hasOptifineCape = false); can be backfilled later if needed.
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

            playerDocuments.add(PlayerDocument.builder()
                    .id(playerUuid)
                    .username(token.getName())
                    .legacyAccount(token.isLegacy())
                    .skin(skinUuid != null ? SkinDocument.builder().id(skinUuid).build() : null)
                    .cape(capeUuid != null ? CapeDocument.builder().id(capeUuid).build() : null)
                    .hasOptifineCape(false)
                    .lastUpdated(now)
                    .firstSeen(now)
                    .build());

            skinHistoryDocuments.add(SkinHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerUuid)
                    .skin(skinUuid != null ? SkinDocument.builder().id(skinUuid).build() : null)
                    .lastUsed(now)
                    .timestamp(now)
                    .build());

            usernameHistoryDocuments.add(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerUuid)
                    .username(token.getName())
                    .timestamp(now)
                    .build());

            if (capeUuid != null) {
                capeHistoryDocuments.add(CapeHistoryDocument.builder()
                        .id(UUID.randomUUID())
                        .playerId(playerUuid)
                        .cape(CapeDocument.builder().id(capeUuid).build())
                        .lastUsed(now)
                        .timestamp(now)
                        .build());
            }

            if (skinUuid != null) {
                skinCounts.merge(skinUuid, 1L, (a, b) -> (a != null ? a : 0L) + (b != null ? b : 0L));
            }
            if (capeUuid != null) {
                capeCounts.merge(capeUuid, 1L, (a, b) -> (a != null ? a : 0L) + (b != null ? b : 0L));
            }
            UUID submittedBy = submission.submittedBy();
            if (submittedBy != null) {
                submitterCounts.merge(submittedBy, 1L, (a, b) -> (a != null ? a : 0L) + (b != null ? b : 0L));
            }
        }

        StatisticsService.updateTrackedPlayerCount(StatisticsService.INSTANCE.getTrackedPlayerCount() + submissions.size());

        mongoTemplate.insert(playerDocuments, PlayerDocument.class);
        mongoTemplate.insert(skinHistoryDocuments, SkinHistoryDocument.class);
        mongoTemplate.insert(usernameHistoryDocuments, UsernameHistoryDocument.class);
        if (!capeHistoryDocuments.isEmpty()) {
            mongoTemplate.insert(capeHistoryDocuments, CapeHistoryDocument.class);
        }

        if (!skinCounts.isEmpty()) {
            BulkOperations skinBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, SkinDocument.class);
            for (Map.Entry<UUID, Long> e : skinCounts.entrySet()) {
                skinBulk.updateOne(
                        Query.query(Criteria.where("_id").is(e.getKey())),
                        new Update().inc("accountsUsed", e.getValue()));
            }
            skinBulk.execute();
        }
        if (!capeCounts.isEmpty()) {
            BulkOperations capeBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, CapeDocument.class);
            for (Map.Entry<UUID, Long> e : capeCounts.entrySet()) {
                capeBulk.updateOne(
                        Query.query(Criteria.where("_id").is(e.getKey())),
                        new Update().inc("accountsOwned", e.getValue()));
            }
            capeBulk.execute();
        }
        if (!submitterCounts.isEmpty()) {
            BulkOperations submitterBulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, PlayerDocument.class);
            for (Map.Entry<UUID, Long> e : submitterCounts.entrySet()) {
                submitterBulk.updateOne(
                        Query.query(Criteria.where("_id").is(e.getKey())),
                        new Update().inc("submittedUuids", e.getValue()));
            }
            submitterBulk.execute();
        }

        log.debug("Bulk created {} players", submissions.size());
    }

    /**
     * Checks if a player exists in the database.
     *
     * @param id the uuid of the player
     * @return true if the player exists, false otherwise
     */
    public boolean exists(UUID id) {
        return this.playerRepository.existsById(id);
    }

    /**
     * Returns which of the given IDs exist in the database (single query).
     *
     * @param ids the uuids to check
     * @return set of ids that exist
     */
    public Set<UUID> getExistingPlayerIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        List<UUID> found = this.mongoTemplate.query(PlayerDocument.class)
                .distinct("_id")
                .as(UUID.class)
                .matching(query)
                .all();
        return Set.copyOf(found);
    }

    /**
     * Search for players whose username starts with the given query, case-insensitive.
     * Uses two queries: one for player docs (id, username, skin), one batch load for all referenced skins.
     *
     * @param query the prefix to match (e.g. "steve" matches "Steve", "STEVE")
     * @return list of matching players with skin, up to {@value #MAX_PLAYER_SEARCH_RESULTS}
     */
    public List<PlayerSearchEntry> searchPlayers(String query) {
        String prefixEnd = query.isEmpty() ? "\uFFFF"
                : query.charAt(query.length() - 1) == Character.MAX_VALUE ? query + "\uFFFF"
                : query.substring(0, query.length() - 1) + (char) (query.charAt(query.length() - 1) + 1);
        Query q = Query.query(Criteria.where("username").gte(query).lt(prefixEnd))
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()))
                .withHint("username_case_insensitive")
                .with(PageRequest.of(0, MAX_PLAYER_SEARCH_RESULTS));
        List<org.bson.Document> docs = MongoUtils.findWithFields(mongoTemplate, q, PlayerDocument.class, "_id", "username", "skin");
        Set<UUID> skinIds = new HashSet<>();
        for (org.bson.Document d : docs) {
            UUID sid = d.get("skin", UUID.class);
            if (sid != null) skinIds.add(sid);
        }
        Map<UUID, Skin> skinById = new HashMap<>();
        if (!skinIds.isEmpty()) {
            for (SkinDocument sd : mongoTemplate.find(Query.query(Criteria.where("_id").in(skinIds)), SkinDocument.class, "skins")) {
                skinById.put(sd.getId(), skinService.fromDocument(sd));
            }
        }
        return docs.stream()
                .map(d -> new PlayerSearchEntry(
                        d.get("_id", UUID.class),
                        d.getString("username"),
                        d.get("skin", UUID.class) != null ? skinById.get(d.get("skin", UUID.class)) : null))
                .toList();
    }

    /**
     * Gets a player by their username.
     *
     * @param username the username of the player
     * @return the player document
     */
    public Optional<PlayerDocument> getPlayerByUsername(String username) {
        List<PlayerDocument> playerDocuments = this.playerRepository.usernameToUuid(username);
        if (playerDocuments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(playerDocuments.getFirst());
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
     * Safe to call when the document has lazy refs (they are resolved on first access).
     *
     * @param document the player document (e.g. from a batch query or findById)
     * @return the player domain object
     */
    public Player fromDocument(PlayerDocument document) {
        Skin skin = document.getSkin() != null ? skinService.fromDocument(document.getSkin()) : null;

        Set<Skin> skinHistory = null;
        if (document.getSkinHistory() != null && !document.getSkinHistory().isEmpty()) {
            skinHistory = new HashSet<>();
            for (SkinHistoryDocument entry : document.getSkinHistory()) {
                if (entry.getSkin() != null) {
                    skinHistory.add(skinService.fromDocument(entry.getSkin()));
                }
            }
        }

        VanillaCape cape = document.getCape() != null ? capeService.fromDocument(document.getCape()) : null;
        Set<VanillaCape> capeHistory = null;
        if (document.getCapeHistory() != null && !document.getCapeHistory().isEmpty()) {
            capeHistory = new HashSet<>();
            for (CapeHistoryDocument entry : document.getCapeHistory()) {
                if (entry.getCape() != null) {
                    capeHistory.add(capeService.fromDocument(entry.getCape()));
                }
            }
        }

        Set<UsernameHistory> usernameHistory = null;
        if (document.getUsernameHistory() != null && !document.getUsernameHistory().isEmpty()) {
            usernameHistory = new HashSet<>();
            for (UsernameHistoryDocument entry : document.getUsernameHistory()) {
                usernameHistory.add(new UsernameHistory(entry.getUsername(), entry.getTimestamp()));
            }
        }

        return new Player(
                document.getId(),
                document.getUsername(),
                document.isLegacyAccount(),
                skin,
                skinHistory,
                cape,
                capeHistory,
                document.isHasOptifineCape(),
                usernameHistory,
                document.getLastUpdated(),
                document.getFirstSeen()
        );
    }
}
