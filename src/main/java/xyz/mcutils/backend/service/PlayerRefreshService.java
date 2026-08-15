package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.exception.impl.RateLimitException;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration REFRESH_LEASE = Duration.ofMinutes(10);

    private final int refreshChunkSize;
    private final int concurrentFetches;
    private final RateLimiter rateLimiter;
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService refreshExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(
            MojangService mojangService,
            PlayerService playerService,
            PlayerRepository playerRepository,
            PlatformTransactionManager transactionManager,
            @Value("${mc-utils.player-refresh.chunk-size:2500}") int refreshChunkSize,
            @Value("${mc-utils.player-refresh.concurrent-fetches:200}") int concurrentFetches,
            @Value("${mc-utils.player-refresh.mojang-rate-limit:600}") double mojangRateLimit
    ) {
        this.refreshChunkSize = refreshChunkSize;
        this.concurrentFetches = concurrentFetches;
        this.rateLimiter = RateLimiter.create(mojangRateLimit);
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.refreshExecutor = Executors.newFixedThreadPool(
                concurrentFetches,
                Thread.ofPlatform().name("player-refresh-worker-", 0).factory()
        );
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
        refreshExecutor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        log.info("Starting player background refresh (chunk size {}, Mojang rate limit {}/s)",
                refreshChunkSize, rateLimiter.getRate());
        Thread.ofPlatform().daemon(true).name("player-refresh").start(() -> {
            while (running.get()) {
                try {
                    Instant now = Instant.now();
                    List<PlayerRow> playerRows = claimDuePlayers(now);
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    log.info("Player refresh claimed {} players", playerRows.size());
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

    private List<PlayerRow> claimDuePlayers(Instant now) {
        return transactionTemplate.execute(status -> {
            // Hot tier first: most-changed and popular players are claimed before stable ones,
            // so their adaptive intervals actually drive refresh frequency even when the loop
            // is overloaded (a plain next_refresh_at FIFO would flatten everyone to the same rate).
            List<PlayerRow> due = new ArrayList<>(playerRepository.findHotDueForRefreshSkippable(
                    now,
                    PageRequest.of(0, refreshChunkSize)
            ));
            if (due.size() < refreshChunkSize) {
                due.addAll(playerRepository.findColdDueForRefreshSkippable(
                        now,
                        PageRequest.of(0, refreshChunkSize - due.size())
                ));
            }
            if (due.isEmpty()) {
                return List.of();
            }
            List<UUID> ids = due.stream().map(PlayerRow::getId).toList();
            playerRepository.leasePlayersForRefresh(ids, now.plus(REFRESH_LEASE));
            return due;
        });
    }

    private void refreshChunk(List<PlayerRow> playerRows) {
        long chunkStart = System.currentTimeMillis();
        List<UUID> failedIds = Collections.synchronizedList(new ArrayList<>());
        Map<String, AtomicInteger> failureReasons = new ConcurrentHashMap<>();
        List<UsernameChangeEventRow> usernameChangeEvents = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger persisted = new AtomicInteger();
        Semaphore inflightFetches = new Semaphore(concurrentFetches);
        List<CompletableFuture<Void>> futures = new ArrayList<>(playerRows.size());

        for (PlayerRow playerRow : playerRows) {
            if (!running.get()) {
                return;
            }
            futures.add(CompletableFuture.runAsync(
                    () -> refreshPlayer(playerRow, failedIds, failureReasons, usernameChangeEvents, persisted, inflightFetches),
                    refreshExecutor
            ));
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
                    "Player refresh chunk finished: persisted={}, failed={}, total={}, {}ms{}",
                    persistedCount,
                    failedIds.size(),
                    playerRows.size(),
                    durationMs,
                    formatFailureReasons(failureReasons)
            );
        } else {
            log.warn(
                    "Player refresh chunk finished with zero outcomes for {} players in {}ms",
                    playerRows.size(),
                    durationMs
            );
        }
    }

    private static String formatFailureReasons(Map<String, AtomicInteger> failureReasons) {
        if (failureReasons.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder(", reasons={");
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> entry : failureReasons.entrySet()) {
            if (!first) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue().get());
            first = false;
        }
        return summary.append('}').toString();
    }

    private static void recordFailure(Map<String, AtomicInteger> failureReasons, String reason) {
        failureReasons.computeIfAbsent(reason, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private void refreshPlayer(
            PlayerRow playerRow,
            List<UUID> failedIds,
            Map<String, AtomicInteger> failureReasons,
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
            PlayerService.PlayerUpdate update = fetchProfile(playerRow, failedIds, failureReasons);
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

    private PlayerService.PlayerUpdate fetchProfile(
            PlayerRow playerRow,
            List<UUID> failedIds,
            Map<String, AtomicInteger> failureReasons
    ) {
        if (!running.get()) {
            return null;
        }
        MetricService.getMetric(PlayerRefreshMetric.class).recordMojangLookup();
        try {
            MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
            if (token == null) {
                failedIds.add(playerRow.getId());
                recordFailure(failureReasons, "null_profile");
                return null;
            }
            return new PlayerService.PlayerUpdate(playerRow, token);
        } catch (RateLimitException e) {
            failedIds.add(playerRow.getId());
            recordFailure(failureReasons, "rate_limited");
            return null;
        } catch (ResourceAccessException e) {
            failedIds.add(playerRow.getId());
            recordFailure(failureReasons, "timeout");
            return null;
        } catch (Exception e) {
            log.debug("Mojang profile lookup failed for {}: {}", playerRow.getId(), e.toString());
            failedIds.add(playerRow.getId());
            recordFailure(failureReasons, e.getClass().getSimpleName());
            return null;
        }
    }
}
