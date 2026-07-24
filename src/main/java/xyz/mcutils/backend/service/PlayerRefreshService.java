package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.metric.impl.player.PlayerRefreshMetric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.UsernameChangeEventRow;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 1000;
    /** Must stay at or below http-client.max-connections-per-route to avoid pool queue stalls. */
    private static final int CONCURRENT_FETCHES = 100;
    private static final int RATE_LIMIT = 400;

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
        Thread.ofVirtual().name("player-refresh").start(() -> {
            while (running.get()) {
                try {
                    Instant now = Instant.now();
                    List<PlayerRow> playerRows = this.playerRepository.findDueForRefresh(
                            now,
                            Pageable.ofSize(REFRESH_CHUNK_SIZE)
                    );
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    refreshChunk(playerRows);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Player refresh loop error, retrying in 5s", e);
                    try {
                        Thread.sleep(Duration.ofSeconds(5));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void refreshChunk(List<PlayerRow> playerRows) {
        // Any lookup failure (null profile, 429, connection timeout, etc.) must still
        // bump nextRefreshAt. Without this, failed players stay permanently at the front of
        // the queue and create a retry storm that saturates the connection pool.
        List<UUID> failedIds = Collections.synchronizedList(new ArrayList<>());
        List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();
        int persisted = 0;

        for (int offset = 0; offset < playerRows.size(); offset += CONCURRENT_FETCHES) {
            if (!running.get()) {
                return;
            }
            int end = Math.min(offset + CONCURRENT_FETCHES, playerRows.size());
            List<PlayerRow> slice = playerRows.subList(offset, end);
            List<CompletableFuture<PlayerService.PersistPlayerRefreshResult>> sliceFutures = new ArrayList<>();

            for (PlayerRow playerRow : slice) {
                rateLimiter.acquire();
                CompletableFuture<PlayerService.PlayerUpdate> fetchFuture = CompletableFuture.supplyAsync(
                        () -> fetchProfile(playerRow, failedIds),
                        Main.EXECUTOR
                );
                sliceFutures.add(fetchFuture.thenApplyAsync(update -> {
                    if (update == null) {
                        return null;
                    }
                    return this.playerService.persistPlayerRefresh(update);
                }, Main.EXECUTOR));
            }

            CompletableFuture.allOf(sliceFutures.toArray(CompletableFuture[]::new)).join();
            for (CompletableFuture<PlayerService.PersistPlayerRefreshResult> future : sliceFutures) {
                PlayerService.PersistPlayerRefreshResult result = future.join();
                if (result != null && result.success()) {
                    persisted++;
                    if (result.usernameChangeEvent() != null) {
                        usernameChangeEvents.add(result.usernameChangeEvent());
                    }
                }
            }
        }

        if (!failedIds.isEmpty()) {
            this.playerService.bumpRefreshFailures(failedIds);
        }
        this.playerService.broadcastUsernameChanges(usernameChangeEvents);
        if (persisted > 0) {
            MetricService.getMetric(PlayerRefreshMetric.class).recordPersist(persisted);
        }
    }

    private PlayerService.PlayerUpdate fetchProfile(PlayerRow playerRow, List<UUID> failedIds) {
        if (!running.get()) {
            return null;
        }
        MetricService.getMetric(PlayerRefreshMetric.class).recordMojangLookup();
        try {
            MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
            if (token == null) {
                failedIds.add(playerRow.getId());
                return null;
            }
            return new PlayerService.PlayerUpdate(playerRow, token);
        } catch (Exception e) {
            failedIds.add(playerRow.getId());
            return null;
        }
    }
}
