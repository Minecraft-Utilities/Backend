package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.metric.impl.player.PlayerRefreshPriorityScoreMetric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 500;
    private static final int RATE_LIMIT = 150;
    public static final double POPULARITY_WEIGHT = 1.5;  // log-scaled monthly views influence on refresh priority
    public static final double VELOCITY_WEIGHT   = 3.0;  // change score influence; higher = frequent changers refreshed sooner
    public static final double URGENCY_WEIGHT    = 0.1;  // overdue time influence; prevents stale players from being starved

    private final RateLimiter rateLimiter = RateLimiter.create(RATE_LIMIT);
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(MojangService mojangService, PlayerService playerService, PlayerRepository playerRepository) {
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (running.get()) {
                try {
                    Instant now = Instant.now();
                    Instant cutoff = now.minus(PlayerService.PLAYER_UPDATE_INTERVAL);
                    List<PlayerRow> playerRows = this.playerRepository.findPlayersForRefresh(
                            cutoff,
                            POPULARITY_WEIGHT,
                            VELOCITY_WEIGHT,
                            URGENCY_WEIGHT,
                            REFRESH_CHUNK_SIZE
                    );
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(30));
                        continue;
                    }
                    PlayerRefreshPriorityScoreMetric priorityScoreMetric = MetricService.getMetric(PlayerRefreshPriorityScoreMetric.class);
                    for (PlayerRow playerRow : playerRows) {
                        priorityScoreMetric.observe(PlayerRow.computeRefreshPriorityScore(
                            playerRow,
                            playerRow.getMonthlyViews(),
                            now, 
                            POPULARITY_WEIGHT, 
                            VELOCITY_WEIGHT, 
                            URGENCY_WEIGHT
                        ));
                    }
                    List<Future<PlayerService.PlayerUpdate>> futures = new ArrayList<>();
                    for (PlayerRow playerRow : playerRows) {
                        rateLimiter.acquire();
                        futures.add(Main.EXECUTOR.submit(() -> {
                            if (!running.get()) {
                                return null;
                            }
                            MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                            if (token == null) {
                                return null;
                            }
                            return new PlayerService.PlayerUpdate(playerRow, token);
                        }));
                    }
                    List<PlayerService.PlayerUpdate> playerUpdates = FutureUtils.awaitAll(futures, "player refresh")
                        .stream()
                        .filter(Objects::nonNull)
                        .toList();
                    this.playerService.updatePlayers(playerUpdates);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}
