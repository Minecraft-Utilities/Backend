package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.player.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.dto.response.PlayerSearchEntry;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

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
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final WebRequest webRequest;
    private final MongoTemplate mongoTemplate;
    private final CoalescingLoader<String, Player> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerRefreshService playerRefreshService,
                         PlayerRepository playerRepository, SkinHistoryRepository skinHistoryRepository, UsernameHistoryRepository usernameHistoryRepository,
                         CapeHistoryRepository capeHistoryRepository, WebRequest webRequest,
                         MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRefreshService = playerRefreshService;
        this.playerRepository = playerRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.webRequest = webRequest;
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
                playerUuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
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
                        this.playerRepository.save(document);
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
     * Creates a new player from their {@link MojangProfileToken}
     *
     * @param token the token for the player
     * @return the created player
     */
    public Player createPlayer(MojangProfileToken token) {
        long start = System.currentTimeMillis();
        UUID playerUuid = UUIDUtils.addDashes(token.getId());

        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        Skin skin = this.skinService.getSkinByTextureId(skinAndCape.left().getTextureId());
        if (skin == null) {
            skin = this.skinService.createSkin(skinAndCape.left(), playerUuid);
        }

        CapeTextureToken capeTextureToken = skinAndCape.right();
        VanillaCape cape = capeTextureToken != null ? this.capeService.getCapeByTextureId(capeTextureToken.getTextureId()) : null;

        UUID skinUuid = skin.getUuid();
        UUID capeUuid = cape != null ? cape.getUuid() : null;

        Boolean hasOptifineCape = false;
        try {
            hasOptifineCape = OptifineCape.capeExists(token.getName(), webRequest).get();
        } catch (Exception ex) {
            log.debug("Optifine cape check failed for {}", token.getName(), ex);
        }

        Date now = new Date();
        PlayerDocument document = this.playerRepository.save(PlayerDocument.builder()
                .id(playerUuid)
                .username(token.getName())
                .legacyAccount(token.isLegacy())
                .skin(skinUuid != null ? SkinDocument.builder().id(skinUuid).build() : null)
                .cape(capeUuid != null ? CapeDocument.builder().id(capeUuid).build() : null)
                .hasOptifineCape(hasOptifineCape)
                .lastUpdated(now)
                .firstSeen(now)
                .build());

        this.skinHistoryRepository.save(SkinHistoryDocument.builder()
                .id(UUID.randomUUID())
                .playerId(playerUuid)
                .skin(SkinDocument.builder().id(skinUuid).build())
                .lastUsed(now)
                .timestamp(now)
                .build());
        if (capeUuid != null) {
            this.capeHistoryRepository.save(CapeHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerUuid)
                    .cape(CapeDocument.builder().id(capeUuid).build())
                    .lastUsed(now)
                    .timestamp(now)
                    .build());
        }
        this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                .id(UUID.randomUUID())
                .playerId(playerUuid)
                .username(token.getName())
                .timestamp(now)
                .build());

        if (capeUuid != null) {
            this.capeService.incrementAccountsOwned(capeUuid);
        }
        this.skinService.incrementAccountsUsed(skinUuid);

        log.debug("Created player {} in {}ms", document.getUsername(), System.currentTimeMillis() - start);
        return new Player(
                document.getId(),
                document.getUsername(),
                document.isLegacyAccount(),
                skin,
                Set.of(skin),
                cape,
                capeUuid != null ? Set.of(cape) : null,
                document.isHasOptifineCape(),
                Set.of(new UsernameHistory(token.getName(), now)),
                new Date(),
                new Date()
        );
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
     *
     * @param query the prefix to match (e.g. "steve" matches "Steve", "STEVE")
     * @return list of matching players with skin
     */
    public List<PlayerSearchEntry> searchPlayers(String query) {
        String prefixEnd = query.isEmpty() ? "\uFFFF"
                : query.charAt(query.length() - 1) == Character.MAX_VALUE ? query + "\uFFFF"
                : query.substring(0, query.length() - 1) + (char) (query.charAt(query.length() - 1) + 1);
        Query q = Query.query(Criteria.where("username").gte(query).lt(prefixEnd))
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()))
                .withHint("username_case_insensitive")
                .with(PageRequest.of(0, MAX_PLAYER_SEARCH_RESULTS));
        return MongoUtils.findWithFields(mongoTemplate, q, PlayerDocument.class, "_id", "username", "skin").stream()
                .map(doc -> {
                    UUID id = doc.get("_id", UUID.class);
                    String username = doc.getString("username");
                    UUID skinId = doc.get("skin", UUID.class);
                    Skin skin = skinId != null ? skinService.fromDocument(mongoTemplate.findById(skinId, SkinDocument.class)) : null;
                    return new PlayerSearchEntry(id, username, skin);
                })
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
