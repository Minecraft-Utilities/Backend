package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayerName;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.redis.PlayerNameCacheRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class PlayerService {
    private static final Duration PLAYER_UPDATE_INTERVAL = Duration.ofHours(3);

    @Value("${mc-utils.cache.player.enabled}")
    private boolean cacheEnabled;

    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerNameCacheRepository playerNameCacheRepository;
    private final PlayerRepository playerRepository;

    @Autowired
    public PlayerService(@NonNull MojangService mojangService, SkinService skinService, CapeService capeService, @NonNull PlayerNameCacheRepository playerNameCacheRepository,
                         @NonNull PlayerRepository playerRepository) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerNameCacheRepository = playerNameCacheRepository;
        this.playerRepository = playerRepository;
    }

    /**
     * Get a player from the database or from the Mojang API.
     *
     * @param query the query to look up the player by (UUID or username)
     * @return the player
     */
    public Player getPlayer(String query) {
        // Convert the id to uppercase to prevent case sensitivity
        UUID uuid = PlayerUtils.getUuidFromString(query);
        if (uuid == null) { // If the id is not a valid uuid, get the uuid from the username
            uuid = usernameToUuid(query).getUniqueId();
        }

        Optional<PlayerDocument> optionalPlayerDocument = this.playerRepository.findById(uuid);
        if (optionalPlayerDocument.isPresent()) {
            PlayerDocument playerDocument = optionalPlayerDocument.get();

            Skin skin = this.skinService.getSkinByUuid(playerDocument.getSkin());

            UUID capeId = playerDocument.getCape();
            VanillaCape cape = capeId != null ? this.capeService.capeCapeByUuid(capeId) : null;

            Player player = new Player(playerDocument.getId(), playerDocument.getUsername(), playerDocument.isLegacyAccount(), skin, cape);

            if (playerDocument.getLastUpdated().toInstant().isBefore(Instant.now().minus(PLAYER_UPDATE_INTERVAL))) {
                MojangProfileToken token = mojangService.getProfile(uuid.toString()); // Get the player profile from Mojang
                if (token == null) {
                    throw new NotFoundException("Player with uuid '%s' was not found".formatted(uuid));
                }
                this.updatePlayer(player, playerDocument, token);
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
    }

    /**
     * Creates a new player from their {@link MojangProfileToken}
     *
     * @param token the token for the player
     * @return the created player
     */
    public Player createPlayer(MojangProfileToken token) {
        long start = System.currentTimeMillis();

        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        Skin skin = this.skinService.getSkinByTextureId(skinAndCape.left().getTextureId());
        if (skin == null) {
            skin = this.skinService.createSkin(skinAndCape.left());
        }

        CapeTextureToken capeTextureToken = skinAndCape.right();
        VanillaCape cape = capeTextureToken != null ? this.capeService.getCapeByTextureId(capeTextureToken.getTextureId()) : null;

        PlayerDocument document = this.playerRepository.insert(new PlayerDocument(
                UUIDUtils.addDashes(token.getId()),
                token.getName(),
                token.isLegacy(),
                skin.getUuid(),
                cape != null ? cape.getUuid() : null,
                new Date()
        ));

        log.debug("Created player {} in {}ms", document.getUsername(), System.currentTimeMillis() - start);
        return new Player(document.getId(), document.getUsername(), document.isLegacyAccount(), skin, cape);
    }

    /**
     * Updates the player with their new data from the {@link MojangProfileToken}
     *
     * @param player the player to update
     * @param document the player's document
     * @param token the player's {@link MojangProfileToken} token
     */
    private void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        long start = System.currentTimeMillis();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        boolean shouldSave = false;

        // Player username
        if (!player.getUsername().equals(token.getName())) {
            document.setUsername(token.getName());
            shouldSave = true;
        }

        // Player skin
        SkinTextureToken skinTextureToken = skinAndCape.left();
        if (!player.getSkin().getTextureId().equals(skinTextureToken.getTextureId())) {
            document.setSkin(this.skinService.getOrCreateSkinByTextureId(skinTextureToken).getUuid());
            shouldSave = true;
        }

        // Player cape
        CapeTextureToken right = skinAndCape.right();
        String capeTextureId = right != null ? right.getTextureId() : null;
        String currentCapeTextureId = player.getCape() != null ? player.getCape().getTextureId() : null;
        if (!Objects.equals(currentCapeTextureId, capeTextureId)) {
            document.setCape(capeTextureId != null ? this.capeService.getCapeByTextureId(capeTextureId).getUuid() : null);
            shouldSave = true;
        }

        // Legacy account status
        if (player.isLegacyAccount() != token.isLegacy()) {
            document.setLegacyAccount(token.isLegacy());
            shouldSave = true;
        }

        if (shouldSave) {
            document.setLastUpdated(new Date());
            this.playerRepository.save(document);
        }
        log.debug("Updated player {} in {}ms", player.getUsername(), System.currentTimeMillis() - start);
    }

    /**
     * Gets the player's uuid from their username.
     *
     * @param username the username of the player
     * @return the uuid of the player
     */
    public CachedPlayerName usernameToUuid(String username) {
        String normalizedUsername = username.toUpperCase();

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerName> cachedPlayerName = playerNameCacheRepository.findById(normalizedUsername);
            if (cachedPlayerName.isPresent()) {
                CachedPlayerName playerName = cachedPlayerName.get();
                log.debug("Got username {} -> {} from cache in {}ms", username, playerName.getUniqueId(), System.currentTimeMillis() - cacheStart);
                playerName.setCached(true);
                return playerName;
            }
        }

        // Check the Mojang API
        long fetchStart = System.currentTimeMillis();
        try {
            MojangUsernameToUuidToken mojangUsernameToUuid = mojangService.getUuidFromUsername(username);
            if (mojangUsernameToUuid == null) {
                throw new NotFoundException("Player with username '%s' was not found".formatted(username));
            }
            UUID uuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
            CachedPlayerName playerName = new CachedPlayerName(normalizedUsername, username, uuid);

            if (cacheEnabled) {
                CompletableFuture.runAsync(() -> this.playerNameCacheRepository.save(playerName), Main.EXECUTOR)
                        .exceptionally(ex -> {
                            log.warn("Save failed for player username lookup {}: {}", playerName, ex.getMessage());
                            return null;
                        });
            }
            log.debug("Got uuid for username {} in {}ms", username, System.currentTimeMillis() - fetchStart);
            return playerName;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
    }
}
