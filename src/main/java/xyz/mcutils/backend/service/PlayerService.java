package xyz.mcutils.backend.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.AppConfig;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.exception.impl.ResourceNotFoundException;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.cache.CachedPlayerSkinPart;
import xyz.mcutils.backend.model.player.UUIDSubmission;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.model.token.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.redis.PlayerCacheRepository;
import xyz.mcutils.backend.repository.redis.PlayerNameCacheRepository;
import xyz.mcutils.backend.repository.redis.PlayerSkinPartCacheRepository;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.UUID;

@Service @Log4j2(topic = "Player Service")
public class PlayerService {

    private final MojangService mojangService;
    private final PlayerRepository playerRepository;
    private final PlayerCacheRepository playerCacheRepository;
    private final PlayerNameCacheRepository playerNameCacheRepository;
    private final PlayerSkinPartCacheRepository playerSkinPartCacheRepository;

    @Autowired
    public PlayerService(MojangService mojangService, PlayerRepository playerRepository, PlayerCacheRepository playerCacheRepository,
                         PlayerNameCacheRepository playerNameCacheRepository, PlayerSkinPartCacheRepository playerSkinPartCacheRepository) {
        this.mojangService = mojangService;
        this.playerRepository = playerRepository;
        this.playerCacheRepository = playerCacheRepository;
        this.playerNameCacheRepository = playerNameCacheRepository;
        this.playerSkinPartCacheRepository = playerSkinPartCacheRepository;
    }

    /**
     * Get a player from the cache or
     * from the Mojang API.
     *
     * @param id the id of the player
     * @return the player
     */
    public CachedPlayer getCachedPlayer(String id) {
        // Convert the id to uppercase to prevent case sensitivity
        log.info("Getting player: {}", id);
        UUID uuid = PlayerUtils.getUuidFromString(id);
        if (uuid == null) { // If the id is not a valid uuid, get the uuid from the username
            uuid = usernameToUuid(id).getUniqueId();
        }

        Optional<CachedPlayer> optionalCachedPlayer = playerCacheRepository.findById(uuid);
        if (optionalCachedPlayer.isPresent() && AppConfig.isProduction()) { // Return the cached player if it exists
            log.info("Player {} is cached", id);
            CachedPlayer player = optionalCachedPlayer.get();
            player.getPlayer().getSkin().populatePartUrls(player.getUniqueId().toString());
            return player;
        }

        Player player = playerRepository.findById(uuid).orElse(null);
        if (player != null) {
            log.info("Found player {} in the database", uuid);
        }
        try {
            if (player == null) {
                log.info("Getting player profile from Mojang: {}", id);
                MojangProfileToken mojangProfile = mojangService.getProfile(uuid.toString()); // Get the player profile from Mojang
                if (mojangProfile == null) {
                    throw new ResourceNotFoundException("Player with UUID '%s' not found".formatted(uuid));
                }
                log.info("Got player profile from Mojang: {}", id);

                player = new Player(mojangProfile);
                playerRepository.save(player);
            }

            CachedPlayer cachedPlayer = new CachedPlayer(uuid, player);
            cachedPlayer.getPlayer().getSkin().populatePartUrls(player.getUniqueId().toString());

            playerCacheRepository.save(cachedPlayer);
            cachedPlayer.setCached(false);
            return cachedPlayer;
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
        log.info("Getting UUID from username: {}", username);
        String id = username.toUpperCase();

        // First check Redis cache
        Optional<CachedPlayerName> cachedPlayerName = playerNameCacheRepository.findById(id);
        if (cachedPlayerName.isPresent() && AppConfig.isProduction()) {
            log.info("UUID for {} is cached", username);
            return cachedPlayerName.get();
        }

        // Then check the database
        Optional<Player> existingPlayer = playerRepository.findByUsernameIgnoreCase(username);
        if (existingPlayer.isPresent()) {
            Player player = existingPlayer.get();
            UUID uuid = player.getUniqueId();
            CachedPlayerName playerName = new CachedPlayerName(id, username, uuid);
            playerNameCacheRepository.save(playerName); // Cache it for future use
            log.info("Found UUID in database: {} -> {}", username, uuid);
            playerName.setCached(false);
            return playerName;
        }

        // Finally resort to Mojang API
        try {
            MojangUsernameToUuidToken mojangUsernameToUuid = mojangService.getUuidFromUsername(username);
            if (mojangUsernameToUuid == null) {
                log.info("Player with username '{}' not found", username);
                throw new ResourceNotFoundException("Player with username '%s' not found".formatted(username));
            }
            UUID uuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
            CachedPlayerName player = new CachedPlayerName(id, username, uuid);
            playerNameCacheRepository.save(player);
            log.info("Got UUID from Mojang API: {} -> {}", username, uuid);
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
        BufferedImage renderedPart = part.render(player.getSkin(), renderOverlay, size); // Render the skin part
        log.info("Took {}ms to render skin part {} for player: {}", System.currentTimeMillis() - before, name, player.getUniqueId());

        CachedPlayerSkinPart skinPart = new CachedPlayerSkinPart(
                key,
                ImageUtils.imageToBytes(renderedPart)
        );
        log.info("Fetched skin part {} for player: {}", name, player.getUniqueId());
        playerSkinPartCacheRepository.save(skinPart);
        return skinPart;
    }
}
