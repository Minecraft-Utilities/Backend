package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final RateLimiter playerUpdateRateLimiter = RateLimiter.create(600.0);
    private static final Duration MIN_TIME_BETWEEN_UPDATES = Duration.ofDays(1);
    private static final int REFRESH_WORKER_THREADS = 250;

    private final ExecutorService refreshWorkers = Executors.newFixedThreadPool(REFRESH_WORKER_THREADS);
    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final WebRequest webRequest;
    private final MongoTemplate mongoTemplate;


    public PlayerRefreshService(MojangService mojangService, SkinService skinService, CapeService capeService, @Lazy PlayerService playerService,
                            PlayerRepository playerRepository, SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository,
                            UsernameHistoryRepository usernameHistoryRepository, WebRequest webRequest, MongoTemplate mongoTemplate) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.webRequest = webRequest;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (true) {
                playerUpdateRateLimiter.acquire();
                try {
                    Date cutoff = Date.from(Instant.now().minus(MIN_TIME_BETWEEN_UPDATES));
                    Page<PlayerDocument> players = this.playerRepository.findByLastUpdatedBeforeOrderByLastUpdatedAsc(cutoff, PageRequest.of(0, 2500));
                    if (players.getTotalElements() > 0) {
                        for (PlayerDocument playerDocument : players) {
                            playerUpdateRateLimiter.acquire();
                            refreshWorkers.submit(() -> {
                                MojangProfileToken token = this.mojangService.getProfile(playerDocument.getId().toString());
                                if (token == null) {
                                    return;
                                }
                                this.updatePlayer(this.playerService.getPlayer(token.getId()), playerDocument, token);
                            });
                        }
                    }
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Updates the player with their new data from the {@link MojangProfileToken}
     *
     * @param player   the player to update
     * @param document the player's document
     * @param token    the player's {@link MojangProfileToken} token
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        Date now = new Date();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        UUID playerId = document.getId();

        updateUsername(player, document, token.getName(), now);
        updateSkin(player, document, skinAndCape.left(), now);
        updateCape(player, document, skinAndCape.right(), now);

        if (player.isLegacyAccount() != token.isLegacy()) {
            document.setLegacyAccount(token.isLegacy());
            player.setLegacyAccount(token.isLegacy());
        }

        OptifineCape.capeExists(token.getName(), webRequest)
                .thenAccept(has -> mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(playerId)),
                        new Update().set("hasOptifineCape", has),
                        PlayerDocument.class
                ));

        document.setLastUpdated(now);
        player.setLastUpdated(now);
        this.playerRepository.save(document);
    }

    /**
     * Updates the player's username from the profile and ensures it is in history.
     *
     * @param player   the player to update
     * @param document the player's document
     * @param newName  the new username from the Mojang profile
     * @param now      the timestamp to use for history entries
     */
    private void updateUsername(Player player, PlayerDocument document, String newName, Date now) {
        UUID playerId = document.getId();
        if (!player.getUsername().equals(newName)) {
            document.setUsername(newName);
            player.setUsername(newName);
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(newName)
                    .timestamp(now)
                    .build());
        }
        String current = document.getUsername();
        boolean currentNotInHistory = current != null && (document.getUsernameHistory() == null
                || document.getUsernameHistory().stream().noneMatch(uh -> current.equals(uh.getUsername())));
        if (currentNotInHistory) {
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(current)
                    .timestamp(now)
                    .build());
        }
    }

    /**
     * Updates the player's current skin from the profile and records skin history.
     *
     * @param player   the player to update
     * @param document the player's document
     * @param skinToken the skin texture from the Mojang profile, or null if no skin
     * @param now      the timestamp to use for history (lastUsed)
     */
    private void updateSkin(Player player, PlayerDocument document, SkinTextureToken skinToken, Date now) {
        if (skinToken == null) {
            return;
        }

        String newTextureId = skinToken.getTextureId();
        String currentTextureId = player.getSkin() != null ? player.getSkin().getTextureId() : null;
        boolean skinChanged = !Objects.equals(currentTextureId, newTextureId);

        if (skinChanged) {
            Skin newSkin = this.skinService.getOrCreateSkinByTextureId(skinToken, player.getUniqueId());
            document.setSkin(SkinDocument.builder().id(newSkin.getUuid()).build());
            player.setSkin(newSkin);
        }

        if (player.getSkin() != null) {
            UUID playerId = document.getId();
            UUID skinId = player.getSkin().getUuid();
            boolean newHistoryEntry = this.skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinId)
                    .map(existing -> {
                        existing.setLastUsed(now);
                        this.skinHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.skinHistoryRepository.save(SkinHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .skin(SkinDocument.builder().id(skinId).build())
                                .lastUsed(now)
                                .timestamp(now)
                                .build());
                        return true;
                    });
            if (newHistoryEntry) {
                this.skinService.incrementAccountsUsed(skinId);
            }
        }
    }

    /**
     * Updates the player's current cape from the profile and records cape history.
     *
     * @param player    the player to update
     * @param document  the player's document
     * @param capeToken the cape texture from the Mojang profile, or null if no cape
     * @param now       the timestamp to use for history (lastUsed)
     */
    private void updateCape(Player player, PlayerDocument document, CapeTextureToken capeToken, Date now) {
        String newTextureId = capeToken != null ? capeToken.getTextureId() : null;
        String currentTextureId = player.getCape() != null ? player.getCape().getTextureId() : null;
        boolean capeChanged = !Objects.equals(currentTextureId, newTextureId);

        if (capeChanged) {
            VanillaCape newCape = newTextureId != null ? this.capeService.getCapeByTextureId(newTextureId) : null;
            document.setCape(newCape != null ? CapeDocument.builder().id(newCape.getUuid()).build() : null);
            player.setCape(newCape);
        }

        if (player.getCape() != null) {
            UUID playerId = document.getId();
            UUID capeId = player.getCape().getUuid();
            boolean newHistoryEntry = this.capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeId)
                    .map(existing -> {
                        existing.setLastUsed(now);
                        this.capeHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.capeHistoryRepository.save(CapeHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .cape(CapeDocument.builder().id(capeId).build())
                                .lastUsed(now)
                                .timestamp(now)
                                .build());
                        return true;
                    });
            if (newHistoryEntry) {
                this.capeService.incrementAccountsOwned(capeId);
            }
        }
    }
}
