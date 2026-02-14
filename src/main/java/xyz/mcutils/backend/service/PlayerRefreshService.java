package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
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
import xyz.mcutils.backend.model.domain.player.UsernameHistory;
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
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final RateLimiter playerUpdateRateLimiter = RateLimiter.create(1000.0);
    private static final Duration MIN_TIME_BETWEEN_UPDATES = Duration.ofHours(1);
    private static final int REFRESH_WORKER_THREADS = 500;

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
                try {
                    Date cutoff = Date.from(Instant.now().minus(MIN_TIME_BETWEEN_UPDATES));
                    List<PlayerDocument> players = this.playerRepository.findListByLastUpdatedBeforeOrderByLastUpdatedAsc(cutoff, PageRequest.of(0, 2500));
                    if (players.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(60).toMillis());
                        continue;
                    }

                    // Process the players
                    List<PlayerDocument> updatedDocs = Collections.synchronizedList(new ArrayList<>());
                    List<Future<?>> futures = new ArrayList<>();
                    for (PlayerDocument playerDocument : players) {
                        Future<?> future = refreshWorkers.submit(() -> {
                            playerUpdateRateLimiter.acquire();
                            MojangProfileToken token = this.mojangService.getProfile(playerDocument.getId().toString());
                            if (token == null) {
                                return;
                            }
                            Player player = this.playerService.fromDocument(playerDocument);
                            this.updatePlayer(player, playerDocument, token);
                            updatedDocs.add(playerDocument);
                        });
                        futures.add(future);
                    }

                    // Wait for all the futures to complete
                    for (Future<?> future : futures) {
                        try {
                            future.get();
                        } catch (ExecutionException e) {
                            log.warn("Player refresh task failed", e.getCause());
                        }
                    }

                    // Save the updated documents in bulk
                    if (!updatedDocs.isEmpty()) {
                        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.ORDERED, PlayerDocument.class);
                        for (PlayerDocument doc : updatedDocs) {
                            bulkOps.replaceOne(Query.query(Criteria.where("_id").is(doc.getId())), doc);
                        }
                        bulkOps.execute();
                    }
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
    }

    /**
     * Updates the player's username from the profile and records username history.
     *
     * @param player   the player to update
     * @param document the player's document
     * @param newName  the new username from the Mojang profile
     * @param now      the timestamp to use for history entries
     */
    private void updateUsername(Player player, PlayerDocument document, String newName, Date now) {
        String current = document.getUsername();
        if (current != null) {
            UUID playerId = document.getId();
            boolean currentNotInHistory = this.usernameHistoryRepository.findFirstByPlayerIdAndUsername(playerId, current)
                    .map(existing -> {
                        existing.setTimestamp(now);
                        this.usernameHistoryRepository.save(existing);
                        return false;
                    })
                    .orElseGet(() -> {
                        this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                                .id(UUID.randomUUID())
                                .playerId(playerId)
                                .username(current)
                                .timestamp(now)
                                .build());
                        return true;
                    });
            if (currentNotInHistory) {
                Set<UsernameHistory> usernameHistory = player.getUsernameHistory();
                if (usernameHistory == null) {
                    usernameHistory = new HashSet<>();
                    player.setUsernameHistory(usernameHistory);
                }
                usernameHistory.add(new UsernameHistory(current, now));
            }
        }

        boolean usernameChanged = !Objects.equals(player.getUsername(), newName);
        if (usernameChanged) {
            UUID playerId = document.getId();
            document.setUsername(newName);
            player.setUsername(newName);
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(playerId)
                    .username(newName)
                    .timestamp(now)
                    .build());
            Set<UsernameHistory> usernameHistory = player.getUsernameHistory();
            if (usernameHistory == null) {
                usernameHistory = new HashSet<>();
                player.setUsernameHistory(usernameHistory);
            }
            usernameHistory.add(new UsernameHistory(newName, now));
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
        if (skinToken != null) {
            String newTextureId = skinToken.getTextureId();
            String currentTextureId = player.getSkin() != null ? player.getSkin().getTextureId() : null;
            boolean skinChanged = !Objects.equals(currentTextureId, newTextureId);

            if (skinChanged) {
                Skin newSkin = this.skinService.getOrCreateSkinByTextureId(skinToken, player.getUniqueId());
                document.setSkin(SkinDocument.builder().id(newSkin.getUuid()).build());
                player.setSkin(newSkin);
            }
        }

        if (player.getSkin() != null) {
            UUID playerId = document.getId();
            UUID skinId = player.getSkin().getUuid();
            boolean currentNotInHistory = this.skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinId)
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
            if (currentNotInHistory) {
                this.skinService.incrementAccountsUsed(skinId);
                Set<Skin> skinHistory = player.getSkinHistory();
                if (skinHistory == null) {
                    skinHistory = new HashSet<>();
                    player.setSkinHistory(skinHistory);
                }
                skinHistory.add(player.getSkin());
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
        if (player.getCape() != null) {
            UUID playerId = document.getId();
            UUID capeId = player.getCape().getUuid();
            boolean currentNotInHistory = this.capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeId)
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
            if (currentNotInHistory) {
                this.capeService.incrementAccountsOwned(capeId);
                Set<VanillaCape> capeHistory = player.getCapeHistory();
                if (capeHistory == null) {
                    capeHistory = new HashSet<>();
                    player.setCapeHistory(capeHistory);
                }
                capeHistory.add(player.getCape());
            }
        }

        String newTextureId = capeToken != null ? capeToken.getTextureId() : null;
        String currentTextureId = player.getCape() != null ? player.getCape().getTextureId() : null;
        boolean capeChanged = !Objects.equals(currentTextureId, newTextureId);

        if (capeChanged) {
            VanillaCape newCape = newTextureId != null ? this.capeService.getCapeByTextureId(newTextureId) : null;
            document.setCape(newCape != null ? CapeDocument.builder().id(newCape.getUuid()).build() : null);
            player.setCape(newCape);
        }
    }
}
