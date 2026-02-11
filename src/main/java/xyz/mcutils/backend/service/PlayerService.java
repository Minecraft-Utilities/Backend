package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private final WebRequest webRequest;
    private final MongoTemplate mongoTemplate;
    private final CoalescingLoader<String, Player> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    @Autowired
    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerRefreshService playerRefreshService,
                         PlayerRepository playerRepository, SkinHistoryRepository skinHistoryRepository,
                         CapeHistoryRepository capeHistoryRepository, WebRequest webRequest,
                         MongoTemplate mongoTemplate) {
        INSTANCE = this;
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRefreshService = playerRefreshService;
        this.playerRepository = playerRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.webRequest = webRequest;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Get a player from the database or from the Mojang API.
     *
     * @param query the query to look up the player by (UUID or username)
     * @return the player
     */
    public Player getPlayer(String query) {
        return playerLoader.get(query, () -> {
            // Convert the id to uppercase to prevent case sensitivity
            UUID uuid = PlayerUtils.getUuidFromString(query);
            if (uuid == null) { // If the id is not a valid uuid, get the uuid from the username
                uuid = this.usernameToUuid(query);
            }

            Optional<PlayerDocument> optionalPlayerDocument = this.playerRepository.findById(uuid);
            if (optionalPlayerDocument.isPresent()) {
                PlayerDocument document = optionalPlayerDocument.get();
                Skin skin = document.getSkin() != null ? skinService.fromDocument(document.getSkin()) : null;
                List<Skin> skinHistory = null;
                if (document.getSkinHistory() != null && !document.getSkinHistory().isEmpty()) {
                    skinHistory = document.getSkinHistory().stream()
                            .map(sh -> sh.getSkin() != null ? skinService.fromDocument(sh.getSkin()) : null)
                            .filter(Objects::nonNull)
                            .toList();
                    if (skinHistory.isEmpty()) {
                        skinHistory = null;
                    }
                }
                VanillaCape cape = document.getCape() != null ? capeService.fromDocument(document.getCape()) : null;
                List<VanillaCape> capeHistory = null;
                if (document.getCapeHistory() != null && !document.getCapeHistory().isEmpty()) {
                    capeHistory = document.getCapeHistory().stream()
                            .map(ch -> ch.getCape() != null ? capeService.fromDocument(ch.getCape()) : null)
                            .filter(Objects::nonNull)
                            .toList();
                    if (capeHistory.isEmpty()) {
                        capeHistory = null;
                    }
                }
                Player player = new Player(document.getId(), document.getUsername(), document.isLegacyAccount(), skin, skinHistory, cape, capeHistory,
                        document.isHasOptifineCape(), document.getLastUpdated(), document.getFirstSeen());
                if (document.getLastUpdated().toInstant().isBefore(Instant.now().minus(PLAYER_UPDATE_INTERVAL))) {
                    MojangProfileToken token = mojangService.getProfile(uuid.toString());
                    if (token == null) {
                        throw new NotFoundException("Player with uuid '%s' was not found".formatted(uuid));
                    }
                    this.playerRefreshService.updatePlayer(player, document, token);
                }
                return player;
            }

            try {
                MojangProfileToken token = mojangService.getProfile(uuid.toString()); // Get the player profile from Mojang
                if (token == null) {
                    throw new NotFoundException("Player with uuid '%s' was not found".formatted(uuid));
                }
                return this.createPlayer(token);
            } catch (RateLimitException exception) {
                throw new MojangAPIRateLimitException();
            }
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
        } catch (Exception ignored) { }

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
                .timestamp(now)
                .build());
        if (capeUuid != null) {
            this.capeHistoryRepository.save(CapeHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerUuid)
                    .cape(CapeDocument.builder().id(capeUuid).build())
                    .timestamp(now)
                    .build());
        }

        if (capeUuid != null) {
            this.capeService.incrementAccountsOwned(capeUuid);
            cape.setAccountsOwned(cape.getAccountsOwned() + 1); 
        }
        this.skinService.incrementAccountsUsed(skinUuid);
        skin.setAccountsUsed(skin.getAccountsUsed() + 1);

        log.debug("Created player {} in {}ms", document.getUsername(), System.currentTimeMillis() - start);
        return new Player(document.getId(), document.getUsername(), document.isLegacyAccount(), skin, List.of(skin), cape,
                capeUuid != null ? List.of(cape) : null, document.isHasOptifineCape(), new Date(), new Date());
    }

    /**
     * Search for players whose username starts with the given query, case-insensitive.
     *
     * @param query the prefix to match (e.g. "steve" matches "Steve", "STEVE")
     * @return list of matching players with skin
     */
    public List<PlayerSearchEntry> searchPlayers(String query) {
        return this.playerRepository.findByUsernameStartingWithIgnoreCase(query, PageRequest.of(0, MAX_PLAYER_SEARCH_RESULTS)).stream()
                .map(doc -> new PlayerSearchEntry(doc.getId(), doc.getUsername(),
                        doc.getSkin() != null ? skinService.fromDocument(doc.getSkin()) : null))
                .toList();
    }

    /**
     * Gets the player's uuid from their username.
     *
     * @param username the username of the player
     * @return the uuid of the player
     */
    public UUID usernameToUuid(String username) {
        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<PlayerDocument> playerDocument = this.playerRepository.usernameToUuid(username).stream().findFirst();
            if (playerDocument.isPresent()) {
                // todo: check if this returns more than 1 player and force a refresh
                //  for all the accounts, since obv accounts cant have the same username
                log.debug("Got uuid for username {} from database in {}ms", username, System.currentTimeMillis() - cacheStart);
                return playerDocument.get().getId();
            }
        }


        // Check the Mojang API
        long fetchStart = System.currentTimeMillis();
        try {
            MojangUsernameToUuidToken mojangUsernameToUuid = this.mojangService.getUuidFromUsername(username);
            if (mojangUsernameToUuid == null) {
                throw new NotFoundException("Player with username '%s' was not found".formatted(username));
            }
            UUID uuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
            PlayerDocument playerDocument = PlayerDocument.builder()
                    .id(uuid)
                    .username(username)
                    .legacyAccount(false)
                    .skin(null)
                    .cape(null)
                    .hasOptifineCape(false)
                    .lastUpdated(new Date(0L))
                    .firstSeen(new Date())
                    .build();

            if (cacheEnabled) {
                CompletableFuture.runAsync(() -> this.playerRepository.save(playerDocument), Main.EXECUTOR)
                        .exceptionally(ex -> {
                            log.warn("Save failed for player username lookup {}: {}", playerDocument.getUsername(), ex.getMessage());
                            return null;
                        });
            }
            log.debug("Got uuid for username {} -> {} in {}ms", username, uuid, System.currentTimeMillis() - fetchStart);
            return uuid;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
    }

    /**
     * Gets the number of tracked players.
     *
     * @return the number of tracked players
     */
    public long getTrackedPlayerCount() {
        return this.mongoTemplate.estimatedCount(PlayerDocument.class);
    }
}
