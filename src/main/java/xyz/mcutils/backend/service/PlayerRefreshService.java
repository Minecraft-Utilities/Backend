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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 1000;
    /** Must stay at or below http-client.max-connections-per-route to avoid pool queue stalls. */
    private static final int CONCURRENT_FETCHES = 100;
    private static final int RATE_LIMIT = 400;
    private static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(10);

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
        // Platform thread: this loop blocks on join/semaphore backpressure and must not pin a
        // virtual-thread carrier while hundreds of blocking Mojang/DB tasks run elsewhere.
        Thread.ofPlatform().daemon(true).name("player-refresh").start(() -> {
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
        long chunkStart = System.currentTimeMillis();
        List<UUID> failedIds = Collections.synchronizedList(new ArrayList<>());
        List<UsernameChangeEventRow> usernameChangeEvents = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger persisted = new AtomicInteger();
        Semaphore inflightFetches = new Semaphore(CONCURRENT_FETCHES);
        List<CompletableFuture<Void>> futures = new ArrayList<>(playerRows.size());

        for (PlayerRow playerRow : playerRows) {
            if (!running.get()) {
                return;
            }
            futures.add(CompletableFuture.runAsync(() -> refreshPlayer(playerRow, failedIds, usernameChangeEvents, persisted, inflightFetches), Main.EXECUTOR));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .orTimeout(CHUNK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            log.error(
                    "Player refresh chunk timed out or failed for {} players after {}ms",
                    playerRows.size(),
                    System.currentTimeMillis() - chunkStart,
                    e
            );
        }

        if (!failedIds.isEmpty()) {
            this.playerService.bumpRefreshFailures(failedIds);
        }
        this.playerService.broadcastUsernameChanges(usernameChangeEvents);
        int persistedCount = persisted.get();
        if (persistedCount > 0) {
            MetricService.getMetric(PlayerRefreshMetric.class).recordPersist(persistedCount);
        }
        long durationMs = System.currentTimeMillis() - chunkStart;
        if (persistedCount > 0 || !failedIds.isEmpty()) {
            log.info(
                    "Player refresh chunk finished: persisted={}, failed={}, total={}, {}ms",
                    persistedCount,
                    failedIds.size(),
                    playerRows.size(),
                    durationMs
            );
        } else {
            log.warn(
                    "Player refresh chunk finished with zero outcomes for {} players in {}ms",
                    playerRows.size(),
                    durationMs
            );
        }
    }

    private void refreshPlayer(
            PlayerRow playerRow,
            List<UUID> failedIds,
            List<UsernameChangeEventRow> usernameChangeEvents,
            AtomicInteger persisted,
            Semaphore inflightFetches
    ) {
        if (!running.get()) {
            return;
        }
        try {
            inflightFetches.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failedIds.add(playerRow.getId());
            return;
        }
        try {
            rateLimiter.acquire();
            PlayerService.PlayerUpdate update = fetchProfile(playerRow, failedIds);
            if (update == null) {
                return;
            }
            PlayerService.PersistPlayerRefreshResult result = this.playerService.persistPlayerRefresh(update);
            if (result.success()) {
                persisted.incrementAndGet();
                if (result.usernameChangeEvent() != null) {
                    usernameChangeEvents.add(result.usernameChangeEvent());
                }
            }
        } finally {
            inflightFetches.release();
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
