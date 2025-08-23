package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.*;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.model.token.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.history.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.UsernameHistoryRepository;
import xyz.mcutils.backend.repository.redis.PlayerCacheRepository;
import xyz.mcutils.backend.repository.redis.PlayerNameCacheRepository;
import xyz.mcutils.backend.repository.redis.PlayerSkinPartCacheRepository;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service @Log4j2(topic = "Player Service")
public class PlayerService {
    private final MojangService mojangService;
    private final PlayerRepository playerRepository;
    private final PlayerCacheRepository playerCacheRepository;
    private final PlayerNameCacheRepository playerNameCacheRepository;
    private final PlayerSkinPartCacheRepository playerSkinPartCacheRepository;

    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;

    @Autowired
    public PlayerService(@NonNull MojangService mojangService, @NonNull PlayerRepository playerRepository, @NonNull PlayerCacheRepository playerCacheRepository,
                         @NonNull PlayerNameCacheRepository playerNameCacheRepository, @NonNull PlayerSkinPartCacheRepository playerSkinPartCacheRepository,
                         @NonNull SkinHistoryRepository skinHistoryRepository, @NonNull CapeHistoryRepository capeHistoryRepository,
                         @NonNull UsernameHistoryRepository usernameHistoryRepository) {
        this.mojangService = mojangService;
        this.playerRepository = playerRepository;
        this.playerCacheRepository = playerCacheRepository;
        this.playerNameCacheRepository = playerNameCacheRepository;
        this.playerSkinPartCacheRepository = playerSkinPartCacheRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
    }

    /**
     * Get a player from the database or
     * from the Mojang API.
     *
     * @param id the id of the player
     * @param enableRefresh whether to enable refreshing
     * @return the player
     */
    public Player getPlayer(String id, boolean enableRefresh) {
        return getPlayerInternal(id, enableRefresh);
    }

    /**
     * Get a player from the cache or
     * from the Mojang API.
     *
     * @param id the id of the player
     * @return the player
     */
    public CachedPlayer getCachedPlayer(String id, boolean enableRefresh) {
        UUID uuid = resolvePlayerUuid(id);

        Optional<CachedPlayer> optionalCachedPlayer = playerCacheRepository.findById(uuid);
        if (optionalCachedPlayer.isPresent() && AppConfig.isProduction()) {
            return optionalCachedPlayer.get();
        }

        try {
            Player player = getPlayerInternal(id, enableRefresh);
            CachedPlayer cachedPlayer = new CachedPlayer(uuid, player);
            playerCacheRepository.save(cachedPlayer);
            cachedPlayer.setCached(false);
            return cachedPlayer;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
    }

    /**
     * Internal method to get a player from the database or Mojang API.
     * Handles the common logic for both getPlayer and getCachedPlayer.
     */
    private Player getPlayerInternal(String id, boolean enableRefresh) {
        UUID uuid = resolvePlayerUuid(id);
        Player player = playerRepository.findById(uuid).orElse(null);

        if (player == null) {
            MojangProfileToken mojangProfile = mojangService.getProfile(uuid.toString());
            if (mojangProfile == null) {
                throw new ResourceNotFoundException("Player with UUID '%s' not found".formatted(uuid));
            }

            player = new Player(mojangProfile, skinHistoryRepository, capeHistoryRepository, usernameHistoryRepository);
            playerRepository.save(player);
        }

        // Execute history queries in parallel
        UUID playerId = player.getUniqueId();
        try {
            CompletableFuture<List<SkinHistoryEntry>> skinFuture = CompletableFuture.supplyAsync(() -> skinHistoryRepository.findByPlayerId(playerId));
            CompletableFuture<List<CapeHistoryEntry>> capeFuture = CompletableFuture.supplyAsync(() -> capeHistoryRepository.findByPlayerId(playerId));
            CompletableFuture<List<UsernameHistoryEntry>> usernameFuture = CompletableFuture.supplyAsync(() -> usernameHistoryRepository.findByPlayerId(playerId));
            
            CompletableFuture.allOf(skinFuture, capeFuture, usernameFuture).get();
            
            // Set results after all queries complete
            player.setSkinHistory(skinFuture.get());
            player.setCapes(capeFuture.get());
            player.setUsernameHistory(usernameFuture.get());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error fetching player history data", e);
            throw new InternalServerErrorException("Failed to fetch player history data");
        }

        if (player.shouldRefresh(enableRefresh)) {
            player.refresh(mojangService, skinHistoryRepository, capeHistoryRepository, usernameHistoryRepository);
            playerRepository.save(player);
        }

        return player;
    }

    /**
     * Resolves a player ID (either UUID or username) to a UUID.
     */
    private UUID resolvePlayerUuid(String id) {
        UUID uuid = PlayerUtils.getUuidFromString(id);
        if (uuid == null) {
            uuid = usernameToUuid(id).getUniqueId();
        }
        return uuid;
    }

    /**
     * Gets the player's uuid from their username.
     *
     * @param username the username of the player
     * @return the uuid of the player
     */
    public CachedPlayerName usernameToUuid(String username) {
        String id = username.toUpperCase();

        // First check Redis cache
        Optional<CachedPlayerName> cachedPlayerName = playerNameCacheRepository.findById(id);
        if (cachedPlayerName.isPresent() && AppConfig.isProduction()) {
            return cachedPlayerName.get();
        }

        // Then check the database
        Optional<Player> existingPlayer = playerRepository.findByUsernameIgnoreCase(username);
        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            UUID uuid = player.getUniqueId();
            CachedPlayerName playerName = new CachedPlayerName(id, username, uuid);
            playerNameCacheRepository.save(playerName); // Cache it for future use
            playerName.setCached(false);
            return playerName;
        }

        // Finally resort to Mojang API
        try {
            MojangUsernameToUuidToken mojangUsernameToUuid = mojangService.getUuidFromUsername(username);
            if (mojangUsernameToUuid == null) {
                throw new ResourceNotFoundException("Player with username '%s' not found".formatted(username));
            }
            UUID uuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
            CachedPlayerName player = new CachedPlayerName(id, username, uuid);
            playerNameCacheRepository.save(player);
            player.setCached(false);
            return player;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
    }

    /**
     * Gets a skin part from the player's skin.
     *
     * @param player the player
     * @param partName the name of the part
     * @param renderOverlay whether to render the overlay
     * @return the skin part
     */
    public CachedPlayerSkinPart getSkinPart(Player player, String partName, boolean renderOverlay, int size) {
        if (size > 512) {
            throw new BadRequestException("Size cannot be larger than 512");
        }
        if (size < 32) {
            throw new BadRequestException("Size cannot be smaller than 32");
        }

        ISkinPart part = ISkinPart.getByName(partName); // The skin part to get
        if (part == null) {
            throw new BadRequestException("Invalid skin part: %s".formatted(partName));
        }

        String name = part.name();
        log.info("Getting skin part {} for player: {} (size: {}, overlays: {})", name, player.getUniqueId(), size, renderOverlay);
        String key = "%s-%s-%s-%s".formatted(player.getUniqueId(), name, size, renderOverlay);

        Optional<CachedPlayerSkinPart> cache = playerSkinPartCacheRepository.findById(key);

        // The skin part is cached
        if (cache.isPresent() && AppConfig.isProduction()) {
            log.info("Skin part {} for player {} is cached", name, player.getUniqueId());
            return cache.get();
        }

        long before = System.currentTimeMillis();
        BufferedImage renderedPart = part.render(player.getCurrentSkin(), renderOverlay, size); // Render the skin part
        log.info("Took {}ms to render skin part {} for player: {}", System.currentTimeMillis() - before, name, player.getUniqueId());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(
                key,
                ImageUtils.imageToBytes(renderedPart)
        );
        log.info("Fetched skin part {} for player: {}", name, player.getUniqueId());
        playerSkinPartCacheRepository.save(skinPart);
        return skinPart;
    }

    /**
     * Gets the top contributors to the server.
     *
     * @return A map of UUIDs to the number of contributions
     */
    public Map<String, Integer> getTopContributors() {
        return playerRepository.findTopContributors(10).stream()
                .collect(Collectors.toMap(Player::getUsername, Player::getUuidsContributed));
    }
}
