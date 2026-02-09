package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.PlayerUtils;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
    private final PlayerRepository playerRepository;
    private final CoalescingLoader<String, Player> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    @Autowired
    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerRepository playerRepository) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
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
                PlayerDocument playerDocument = optionalPlayerDocument.get();

                UUID skinId = playerDocument.getSkin();
                Skin skin = skinId != null ? this.skinService.getSkinByUuid(skinId) : null;
                List<PlayerDocument.HistoryItem> skinHistoryItems = playerDocument.getSkinHistory();
                List<Skin> skinHistory = skinHistoryItems != null ? skinHistoryItems.stream()
                        .filter(historyItem -> historyItem.uuid() != null)
                        .map(historyItem -> this.skinService.getSkinByUuid(historyItem.uuid()))
                        .toList() : null;

                UUID capeId = playerDocument.getCape();
                VanillaCape cape = capeId != null ? this.capeService.getCapeByUuid(capeId) : null;
                List<PlayerDocument.HistoryItem> capeHistoryItems = playerDocument.getCapeHistory();
                List<VanillaCape> capeHistory = capeHistoryItems != null ? capeHistoryItems.stream()
                        .filter(historyItem -> historyItem.uuid() != null)
                        .map(historyItem -> this.capeService.getCapeByUuid(historyItem.uuid()))
                        .toList() : null;

                Player player = new Player(playerDocument.getId(), playerDocument.getUsername(), playerDocument.isLegacyAccount(), skin,
                        skinHistory, cape, capeHistory, playerDocument.isHasOptifineCape(), playerDocument.getLastUpdated(), playerDocument.getFirstSeen());

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

        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        Skin skin = this.skinService.getSkinByTextureId(skinAndCape.left().getTextureId());
        if (skin == null) {
            skin = this.skinService.createSkin(skinAndCape.left());
        }

        CapeTextureToken capeTextureToken = skinAndCape.right();
        VanillaCape cape = capeTextureToken != null ? this.capeService.getCapeByTextureId(capeTextureToken.getTextureId()) : null;

        UUID skinUuid = skin.getUuid();
        UUID capeUuid = cape != null ? cape.getUuid() : null;

        Boolean hasOptifineCape = false;
        try {
            hasOptifineCape = OptifineCape.capeExists(token.getName()).get();
        } catch (Exception ignored) { }

        PlayerDocument document = this.playerRepository.save(new PlayerDocument(
                UUIDUtils.addDashes(token.getId()),
                token.getName(),
                token.isLegacy(),
                skinUuid,
                List.of(new PlayerDocument.HistoryItem(skinUuid, new Date())),
                capeUuid,
                capeUuid != null ? List.of(new PlayerDocument.HistoryItem(capeUuid, new Date())) : null,
                hasOptifineCape,
                new Date(),
                new Date()
        ));

        // Increment the accounts owned for the cape
        if (capeUuid != null) {
            this.capeService.incrementAccountsOwned(capeUuid);
        }
        this.skinService.incrementAccountsUsed(skinUuid);

        log.debug("Created player {} in {}ms", document.getUsername(), System.currentTimeMillis() - start);
        return new Player(document.getId(), document.getUsername(), document.isLegacyAccount(), skin, List.of(skin), cape,
                capeUuid != null ? List.of(cape) : null, document.isHasOptifineCape(), new Date(), new Date());
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

        // Player username
        if (!player.getUsername().equals(token.getName())) {
            document.setUsername(token.getName());
            player.setUsername(token.getName());
        }

        // Player skin
        SkinTextureToken skinTextureToken = skinAndCape.left();
        String skinTextureId = skinTextureToken != null ? skinTextureToken.getTextureId() : null;
        String currentSkinTextureId = player.getSkin() != null ? player.getSkin().getTextureId() : null;
        if (!Objects.equals(currentSkinTextureId, skinTextureId) && skinTextureToken != null) {
            Skin newSkin = this.skinService.getOrCreateSkinByTextureId(skinTextureToken);
            document.setSkin(newSkin.getUuid());
            player.setSkin(newSkin);

            boolean skinInHistory = document.getSkinHistory().stream().anyMatch(historyItem -> historyItem.uuid().equals(newSkin.getUuid()));
            if (!skinInHistory) {
                ArrayList<PlayerDocument.HistoryItem> historyItems = new ArrayList<>(document.getSkinHistory());
                historyItems.add(new PlayerDocument.HistoryItem(newSkin.getUuid(), new Date()));
                document.setSkinHistory(historyItems);

                this.skinService.incrementAccountsUsed(newSkin.getUuid());
            }
        }

        // Player cape
        CapeTextureToken capeTextureToken = skinAndCape.right();
        String capeTextureId = capeTextureToken != null ? capeTextureToken.getTextureId() : null;
        String currentCapeTextureId = player.getCape() != null ? player.getCape().getTextureId() : null;
        if (!Objects.equals(currentCapeTextureId, capeTextureId)) {
            VanillaCape newCape = this.capeService.getCapeByTextureId(capeTextureId);
            if (newCape != null) {
                document.setCape(capeTextureId != null ? newCape.getUuid() : null);
                player.setCape(newCape);

                boolean capeInHistory = document.getCapeHistory().stream().anyMatch(historyItem -> historyItem.uuid().equals(newCape.getUuid()));
                if (!capeInHistory) {
                    ArrayList<PlayerDocument.HistoryItem> historyItems = new ArrayList<>(document.getCapeHistory());
                    historyItems.add(new PlayerDocument.HistoryItem(newCape.getUuid(), new Date()));
                    document.setCapeHistory(historyItems);

                    this.capeService.incrementAccountsOwned(newCape.getUuid());
                }
            }
        }

        // Legacy account status
        if (player.isLegacyAccount() != token.isLegacy()) {
            document.setLegacyAccount(token.isLegacy());
            player.setLegacyAccount(token.isLegacy());
        }

        // Optifine cape
        Boolean hasOptifineCape = false;
        try {
            hasOptifineCape = OptifineCape.capeExists(token.getName()).get();
        } catch (Exception ignored) { }
        document.setHasOptifineCape(hasOptifineCape);

        Date now = new Date();
        document.setLastUpdated(now);
        player.setLastUpdated(now);
        this.playerRepository.save(document);

        log.debug("Updated player {} in {}ms", player.getUsername(), System.currentTimeMillis() - start);
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
            MojangUsernameToUuidToken mojangUsernameToUuid = mojangService.getUuidFromUsername(username);
            if (mojangUsernameToUuid == null) {
                throw new NotFoundException("Player with username '%s' was not found".formatted(username));
            }
            UUID uuid = UUIDUtils.addDashes(mojangUsernameToUuid.getUuid());
            PlayerDocument playerDocument = new PlayerDocument(uuid, username, false, null, null,
                    null, null, false, new Date(0L), new Date());

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
}
