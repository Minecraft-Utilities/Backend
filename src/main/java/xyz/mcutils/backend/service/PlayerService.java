package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.exception.impl.ResourceNotFoundException;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.model.token.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.PlayerCacheRepository;
import xyz.mcutils.backend.repository.PlayerNameCacheRepository;

import java.util.Optional;
import java.util.UUID;

@Service @Log4j2(topic = "Player Service")
public class PlayerService {
    public static PlayerService INSTANCE;

    private final MojangService mojangService;
    private final PlayerNameCacheRepository playerNameCacheRepository;
    private final PlayerCacheRepository playerCacheRepository;

    @Autowired
    public PlayerService(@NonNull MojangService mojangService, @NonNull PlayerNameCacheRepository playerNameCacheRepository, @NonNull PlayerCacheRepository playerCacheRepository) {
        INSTANCE = this;
        this.mojangService = mojangService;
        this.playerNameCacheRepository = playerNameCacheRepository;
        this.playerCacheRepository = playerCacheRepository;
    }

    /**
     * Get a player from the database or
     * from the Mojang API.
     *
     * @param query the query to look up the player by
     * @return the player
     */
    public CachedPlayer getPlayer(String query) {
        // Convert the id to uppercase to prevent case sensitivity
        UUID uuid = PlayerUtils.getUuidFromString(query);
        if (uuid == null) { // If the id is not a valid uuid, get the uuid from the username
            log.info("Getting player uuid for {}", query);
            uuid = usernameToUuid(query).getUniqueId();
            log.info("Found uuid {} for {}", uuid.toString(), query);
        }

        Optional<CachedPlayer> cachedPlayer = playerCacheRepository.findById(uuid);
        if (cachedPlayer.isPresent() && AppConfig.isProduction()) { // Return the cached player if it exists
            log.info("Player {} is cached", query);
            return cachedPlayer.get();
        }

        try {
            log.info("Getting player profile from Mojang for {}", query);
            MojangProfileToken mojangProfile = mojangService.getProfile(uuid.toString()); // Get the player profile from Mojang
            log.info("Got player profile from Mojang for {}", query);
            CachedPlayer player = new CachedPlayer(
                    uuid, // Player UUID
                    new Player(mojangProfile)
            );

            playerCacheRepository.save(player);
            player.setCached(false);
            return player;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
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
        if (cachedPlayerName.isPresent()) {
            return cachedPlayerName.get();
        }

        // Check the Mojang API
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
}
