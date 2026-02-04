package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.PlayerCacheRepository;
import xyz.mcutils.backend.repository.PlayerNameCacheRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PlayerService {
    private final MojangService mojangService;
    private final PlayerNameCacheRepository playerNameCacheRepository;
    private final PlayerCacheRepository playerCacheRepository;

    @Value("${mc-utils.cache.player.enabled}")
    private boolean cacheEnabled;

    @Autowired
    public PlayerService(@NonNull MojangService mojangService, @NonNull PlayerNameCacheRepository playerNameCacheRepository, @NonNull PlayerCacheRepository playerCacheRepository) {
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
            uuid = usernameToUuid(query).getUniqueId();
        }

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayer> cachedPlayer = playerCacheRepository.findById(uuid);
            if (cachedPlayer.isPresent()) {
                log.debug("Got player {} from cache in {}ms", uuid, System.currentTimeMillis() - cacheStart);
                CachedPlayer player = cachedPlayer.get();
                player.setCached(true);
                return player;
            }
        }

        long fetchStart = System.currentTimeMillis();
        try {
            MojangProfileToken mojangProfile = mojangService.getProfile(uuid.toString()); // Get the player profile from Mojang
            if (mojangProfile == null) {
                throw new NotFoundException("Player with uuid '%s' was not found".formatted(uuid));
            }
            CachedPlayer player = new CachedPlayer(
                    uuid, // Player UUID
                    new Player(mojangProfile)
            );

            if (cacheEnabled) {
                this.playerCacheRepository.save(player);
            }
            log.debug("Got player {} from Mojang API in {}ms", uuid, System.currentTimeMillis() - fetchStart);
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

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedPlayerName> cachedPlayerName = playerNameCacheRepository.findById(id);
            if (cachedPlayerName.isPresent()) {
                log.debug("Got username {} from cache in {}ms", username, System.currentTimeMillis() - cacheStart);
                CachedPlayerName playerName = cachedPlayerName.get();
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
            CachedPlayerName playerName = new CachedPlayerName(id, username, uuid);

            if (cacheEnabled) {
                this.playerNameCacheRepository.save(playerName);
            }
            log.debug("Got uuid for username {} in {}ms", username, System.currentTimeMillis() - fetchStart);
            return playerName;
        } catch (RateLimitException exception) {
            throw new MojangAPIRateLimitException();
        }
    }
}
