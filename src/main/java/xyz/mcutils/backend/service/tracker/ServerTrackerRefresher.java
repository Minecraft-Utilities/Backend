package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.token.server.JavaServerStatusToken;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Refresh cycle of the internet server tracker: every tracked server is <b>equal</b> — no
 * velocity, no priority tiers, no scheduled timestamps. The cycle continuously claims the
 * least-recently-refreshed servers (one {@code last_refreshed} column, one min-gap filter,
 * {@code FOR UPDATE SKIP LOCKED} + claim-time bump) and pings them with the same token-only
 * status protocol the verify path uses.
 * <p>
 * Concurrency story: the claim is a single atomic {@code UPDATE ... RETURNING} that bumps
 * {@code last_refreshed} at claim time, so a slow or timed-out chunk cannot re-claim or hammer
 * anything, overlapping instances/restarts cannot double-claim, and a crash mid-chunk costs
 * nothing (rows rotate back after the rest of the set). Anti-poisoning parity: every refresh
 * sample runs the same {@link HoneypotDetector} verdict as discovery; flagged servers persist
 * counts/version only, never samples, and the verifier skips honeypot-flagged hosts entirely
 * (no port walk). The refresh cycle never enqueues players.
 * <p>
 * Not a Spring bean: {@link ServerTrackerService} constructs it after the context is ready.
 */
@Slf4j
public class ServerTrackerRefresher {

    private static final DNSRecord[] NO_RECORDS = new DNSRecord[0];
    private static final long IDLE_SLEEP_MS = 10_000;
    private static final long ERROR_SLEEP_MS = 5_000;
    private static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(10);

    private final JdbcTemplate jdbcTemplate;
    private final JavaMinecraftServerPinger pinger;
    private final HoneypotDetector honeypotDetector;
    private final ServerTrackerStore store;
    private final ServerTrackerMetric metrics; // nullable: metrics recording is optional
    private final Runnable afterChunk;
    private final int chunkSize;
    private final int concurrentFetches;
    private final int timeoutMs;
    private final long minGapHours;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService workerPool;
    private volatile Thread loopThread;

    public ServerTrackerRefresher(
            JdbcTemplate jdbcTemplate,
            JavaMinecraftServerPinger pinger,
            HoneypotDetector honeypotDetector,
            ServerTrackerStore store,
            ServerTrackerMetric metrics, // nullable
            Runnable afterChunk,
            int chunkSize,
            int concurrentFetches,
            int timeoutMs,
            long minGapHours
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.pinger = pinger;
        this.honeypotDetector = honeypotDetector;
        this.store = store;
        this.metrics = metrics;
        this.afterChunk = afterChunk;
        this.chunkSize = chunkSize;
        this.concurrentFetches = concurrentFetches;
        this.timeoutMs = timeoutMs;
        this.minGapHours = minGapHours;
        this.workerPool = Executors.newFixedThreadPool(
                concurrentFetches,
                Thread.ofPlatform().daemon(true).name("tracker-refresh-", 0).factory()
        );
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.loopThread = Thread.ofPlatform().daemon(true).name("tracker-refresh").start(this::runLoop);
        log.info("Server tracker refresh cycle started (chunk size {}, concurrent fetches {}, min gap {}h)",
                chunkSize, concurrentFetches, minGapHours);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        workerPool.shutdownNow();
        if (loopThread != null) {
            try {
                loopThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Server tracker refresh cycle stopped");
    }

    private void runLoop() {
        while (running.get()) {
            try {
                List<ClaimedServer> claimed = claim();
                if (claimed.isEmpty()) {
                    Thread.sleep(IDLE_SLEEP_MS);
                    continue;
                }
                processChunk(claimed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Server tracker refresh cycle error, retrying in {}s", ERROR_SLEEP_MS / 1000, e);
                try {
                    Thread.sleep(ERROR_SLEEP_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private record ClaimedServer(UUID uuid, String ip, int port) {}

    /**
     * Atomically claims the least-recently-refreshed servers and bumps their {@code last_refreshed}
     * in the same statement; {@code FOR UPDATE SKIP LOCKED} keeps overlapping instances from
     * double-claiming.
     */
    private List<ClaimedServer> claim() {
        String sql = """
                UPDATE tracked_servers SET last_refreshed = now()
                WHERE uuid IN (
                    SELECT uuid FROM tracked_servers
                    WHERE last_refreshed < now() - make_interval(hours => ?)
                    ORDER BY last_refreshed ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING uuid, ip, port
                """;
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setInt(1, Math.toIntExact(minGapHours));
                    ps.setInt(2, chunkSize);
                },
                (rs, rowNum) -> new ClaimedServer(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("ip"),
                        rs.getInt("port")
                ));
    }

    private void processChunk(List<ClaimedServer> claimed) {
        long chunkStart = System.currentTimeMillis();
        Semaphore inflight = new Semaphore(concurrentFetches);
        AtomicInteger persisted = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ConcurrentHashMap<String, AtomicInteger> failureReasons = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(claimed.size());

        for (ClaimedServer server : claimed) {
            if (!running.get()) {
                return;
            }
            futures.add(CompletableFuture.runAsync(
                    () -> refreshServer(server, inflight, persisted, failed, failureReasons),
                    workerPool
            ));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .orTimeout(CHUNK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            log.error("Server tracker refresh chunk failed for {} servers after {}ms: {}",
                    claimed.size(), System.currentTimeMillis() - chunkStart, e.toString());
        }

        int persistedCount = persisted.get();
        if (persistedCount > 0 && metrics != null) {
            metrics.recordRefreshPersisted(persistedCount);
        }
        log.info("Server tracker refresh chunk finished: claimed={}, persisted={}, failed={}, {}ms{}",
                claimed.size(), persistedCount, failed.get(), System.currentTimeMillis() - chunkStart,
                formatFailureReasons(failureReasons));
        try {
            afterChunk.run();
        } catch (Exception e) {
            log.warn("Post-refresh stats hook failed: {}", e.toString());
        }
    }

    private void refreshServer(
            ClaimedServer server,
            Semaphore inflight,
            AtomicInteger persisted,
            AtomicInteger failed,
            ConcurrentHashMap<String, AtomicInteger> failureReasons
    ) {
        if (!running.get()) {
            return;
        }
        try {
            inflight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.incrementAndGet();
            return;
        }
        try {
            if (metrics != null) {
                metrics.recordRefreshPing();
            }
            long startNanos = System.nanoTime();
            JavaServerStatusToken token = ping(server);
            if (token == null) {
                recordOffline(server.uuid());
                failed.incrementAndGet();
                return;
            }
            int latencyMs = (int) ((System.nanoTime() - startNanos) / 1_000_000L);
            HoneypotDetector.Verdict verdict = ServerTrackerVerifier.evaluate(honeypotDetector, server.ip(), server.port(), token);
            for (String reason : verdict.dropReasons()) {
                if (metrics != null) {
                    metrics.recordSampleDropped(reason);
                }
            }
            store.record(ServerTrackerVerifier.buildSnapshot(server.ip(), server.port(), token, latencyMs, verdict));
            persisted.incrementAndGet();
        } catch (Exception e) {
            recordOffline(server.uuid());
            failed.incrementAndGet();
            recordFailure(failureReasons, e.getClass().getSimpleName());
        } finally {
            inflight.release();
        }
    }

    /**
     * @return the parsed token when the port answers a valid Java status ping, else null
     */
    private JavaServerStatusToken ping(ClaimedServer server) {
        try {
            JavaServerStatusToken token = pinger.pingToken(server.ip(), server.ip(), server.port(), NO_RECORDS, timeoutMs);
            if (token == null || token.getVersion() == null || token.getPlayers() == null) {
                if (metrics != null) {
                    metrics.recordRefreshFailed("invalid_payload");
                }
                return null;
            }
            return token;
        } catch (RuntimeException e) { // refused, timed out, or a non-MC service replied garbage
            if (metrics != null) {
                metrics.recordRefreshFailed(reasonFor(e));
            }
            return null;
        }
    }

    private static String reasonFor(RuntimeException e) {
        String message = e.getLocalizedMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        if (message.contains("did not respond")) {
            return "unreachable";
        }
        if (message.contains("Unknown hostname")) {
            return "unknown_host";
        }
        return e.getClass().getSimpleName();
    }

    private void recordOffline(UUID serverUuid) {
        try {
            jdbcTemplate.update("UPDATE tracked_servers SET consecutive_offline = consecutive_offline + 1 WHERE uuid = ?", serverUuid);
        } catch (Exception e) {
            log.debug("Failed to bump consecutive_offline for {}: {}", serverUuid, e.toString());
        }
    }

    private static void recordFailure(ConcurrentHashMap<String, AtomicInteger> failureReasons, String reason) {
        failureReasons.computeIfAbsent(reason, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private static String formatFailureReasons(ConcurrentHashMap<String, AtomicInteger> failureReasons) {
        if (failureReasons.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder(", reasons={");
        boolean first = true;
        for (var entry : failureReasons.entrySet()) {
            if (!first) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue().get());
            first = false;
        }
        return summary.append('}').toString();
    }
}