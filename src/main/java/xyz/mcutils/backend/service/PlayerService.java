package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.InternalServerErrorException;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.exception.impl.ResourceNotFoundException;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.model.token.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.history.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.UsernameHistoryRepository;
import xyz.mcutils.backend.repository.redis.PlayerNameCacheRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service @Log4j2(topic = "Player Service")
public class PlayerService {
    public static PlayerService INSTANCE;

    private final MojangService mojangService;
    private final PlayerRepository playerRepository;
    private final PlayerNameCacheRepository playerNameCacheRepository;

    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;

    @Autowired
    public PlayerService(@NonNull MojangService mojangService, @NonNull PlayerRepository playerRepository, @NonNull PlayerNameCacheRepository playerNameCacheRepository,
                         @NonNull SkinHistoryRepository skinHistoryRepository, @NonNull CapeHistoryRepository capeHistoryRepository, @NonNull UsernameHistoryRepository usernameHistoryRepository) {
        INSTANCE = this;
        this.mojangService = mojangService;
        this.playerRepository = playerRepository;
        this.playerNameCacheRepository = playerNameCacheRepository;
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
     * Internal method to get a player from the database or Mojang API.
     * Handles the common logic for both getPlayer and getCachedPlayer.
     */
    private Player getPlayerInternal(String id, boolean enableRefresh) {
        UUID uuid = resolvePlayerUuid(id);
        Player player = playerRepository.findById(uuid).orElse(null);

        if (player == null) {
            MojangProfileToken token = mojangService.getProfile(uuid.toString());
            if (token == null) {
                throw new ResourceNotFoundException("Player with UUID '%s' not found".formatted(uuid));
            }

            player = new Player(token, skinHistoryRepository, capeHistoryRepository, usernameHistoryRepository);
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
     * Gets the top contributors to the server.
     *
     * @return A map of UUIDs to the number of contributions
     */
    public Map<String, Integer> getTopContributors() {
        return playerRepository.findTopContributors(10).stream()
                .collect(Collectors.toMap(Player::getUsername, Player::getUuidsContributed));
    }

    /**
     * Gets the total number of players in the database.
     *
     * @return the total number of players
     */
    public int getTotalPlayers() {
        return (int) playerRepository.count();
    }
}
