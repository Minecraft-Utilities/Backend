package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
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
@Log4j2
public class PlayerRefreshService {
    private static final Duration MIN_TIME_BETWEEN_UPDATES = Duration.ofDays(1);
    private static final int REFRESH_WORKER_THREADS = 150;

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

    private final RateLimiter playerUpdateRateLimiter = RateLimiter.create(100.0);

    @Autowired
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
                    Page<PlayerDocument> players = this.playerRepository.findByLastUpdatedBeforeOrderByLastUpdatedAsc(cutoff, PageRequest.of(0, 100));
                    if (players.getTotalElements() > 0) {
                        log.info("Found {} players to update", players.getTotalElements());
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
     * @param player the player to update
     * @param document the player's document
     * @param token the player's {@link MojangProfileToken} token
     */
    @SneakyThrows
    public void updatePlayer(Player player, PlayerDocument document, MojangProfileToken token) {
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();

        // Player username
        if (!player.getUsername().equals(token.getName())) {
            document.setUsername(token.getName());
            player.setUsername(token.getName());
            Date now = new Date();
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(document.getId())
                    .username(token.getName())
                    .timestamp(now)
                    .build());
        }
        String currentUsername = document.getUsername();
        boolean usernameInHistory = document.getUsernameHistory() != null && document.getUsernameHistory().stream()
                .anyMatch(uh -> currentUsername != null && currentUsername.equals(uh.getUsername()));
        if (!usernameInHistory && currentUsername != null) {
            this.usernameHistoryRepository.save(UsernameHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(document.getId())
                    .username(currentUsername)
                    .timestamp(new Date())
                    .build());
        }

        // Player skin
        SkinTextureToken skinTextureToken = skinAndCape.left();
        String skinTextureId = skinTextureToken != null ? skinTextureToken.getTextureId() : null;
        String currentSkinTextureId = player.getSkin() != null ? player.getSkin().getTextureId() : null;
        if (!Objects.equals(currentSkinTextureId, skinTextureId) && skinTextureToken != null) {
            Skin newSkin = this.skinService.getOrCreateSkinByTextureId(skinTextureToken, player.getUniqueId());
            document.setSkin(SkinDocument.builder().id(newSkin.getUuid()).build());
            player.setSkin(newSkin);

            Date now = new Date();
            this.skinHistoryRepository.save(SkinHistoryDocument.builder()
                    .id(UUID.randomUUID())
                    .playerId(document.getId())
                    .skin(SkinDocument.builder().id(newSkin.getUuid()).build())
                    .timestamp(now)
                    .build());
            boolean skinInHistory = document.getSkinHistory() != null && document.getSkinHistory().stream()
                    .anyMatch(sh -> sh.getSkin() != null && sh.getSkin().getId().equals(newSkin.getUuid()));
            if (!skinInHistory) {
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
                document.setCape(CapeDocument.builder().id(newCape.getUuid()).build());
                player.setCape(newCape);

                Date now = new Date();
                this.capeHistoryRepository.save(CapeHistoryDocument.builder()
                        .id(UUID.randomUUID())
                        .playerId(document.getId())
                        .cape(CapeDocument.builder().id(newCape.getUuid()).build())
                        .timestamp(now)
                        .build());
                boolean capeInHistory = document.getCapeHistory() != null && document.getCapeHistory().stream()
                        .anyMatch(ch -> ch.getCape() != null && ch.getCape().getId().equals(newCape.getUuid()));
                if (!capeInHistory) {
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
        UUID playerId = document.getId();
        OptifineCape.capeExists(token.getName(), webRequest)
                .thenAccept(has -> mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(playerId)),
                        new Update().set("hasOptifineCape", has),
                        PlayerDocument.class
                ));

        Date now = new Date();
        document.setLastUpdated(now);
        player.setLastUpdated(now);
        this.playerRepository.save(document);
    }
}
