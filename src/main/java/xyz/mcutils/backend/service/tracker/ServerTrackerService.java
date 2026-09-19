package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.persistence.postgres.TrackerProgressRow;
import xyz.mcutils.backend.repository.postgres.TrackerProgressRepository;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerSubmitService;
import xyz.mcutils.backend.service.TrackerStatsService;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the internet server tracker: the discovery campaign (IPv4 sweep, verification,
 * harvest, progress persistence) and the refresh cycle (continuous equal-cadence re-ping of
 * tracked servers) run as independent daemon loops sharing the verify pool and the
 * {@link ServerTrackerStore}. Discovery defaults to enabled ({@code mc-utils.server-tracker.enabled});
 * when disabled the refresh cycle still keeps tracked rows current (e.g. after a finished
 * campaign). Harvested players are fed into {@link PlayerSubmitService} attributed to
 * ImFascinated.
 */
@Service
@Slf4j
public class ServerTrackerService {

    /**
     * ImFascinated's UUID: all tracker harvests are attributed to this account so
     * {@code TopSubmittedPlayers} counts them there.
     */
    public static final UUID SUBMITTED_BY = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48");

    private static final long PROGRESS_PERSIST_INTERVAL_SECONDS = 30;
    private static final long FLUSH_INTERVAL_SECONDS = 2;

    private final PlayerSubmitService playerSubmitService;
    private final ServerTrackerStore serverTrackerStore;
    private final TrackerProgressRepository trackerProgressRepository;
    private final HoneypotDetector honeypotDetector;
    private final PlayerSampleVerifier playerSampleVerifier;
    private final JdbcTemplate jdbcTemplate;
    private final TrackerStatsService trackerStatsService;

    private final boolean enabled;
    private final boolean refreshEnabled;
    private final int refreshChunkSize;
    private final int refreshConcurrentFetches;
    private final int refreshTimeoutMs;
    private final long refreshMinGapHours;
    private final int verifyConcurrency;
    private final int enqueueBatchSize;
    private final long enqueueRatePerMinute;
    private final int progressLogIntervalSeconds;
    private final List<String> excludeExtraCidrs;
    private final List<String> includeCidrs;
    private final String discoveryPortsCsv;
    private final int portWindow;
    private final int maxPort;
    private final int probeCapPerIp;
    private final int verifyTimeoutMs;
    private final int discoveryConcurrency;
    private final long connectTimeoutMs;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Verified servers (sink calls) and players handed to the queue, for progress logging. */
    private final AtomicLong serversFound = new AtomicLong();
    private volatile long lastLogCompleted24s;
    private volatile long lastLogNanos = System.nanoTime();

    private ServerTrackerDiscovery discovery;
    private Ipv4Space space;
    private ServerTrackerMetric metrics;
    private BufferedHarvester harvester;
    private ServerTrackerRefresher refresher;
    private ExecutorService verifierExecutor;
    private ScheduledExecutorService upkeepExecutor;
    private Thread discoveryThread;

    public ServerTrackerService(
            PlayerSubmitService playerSubmitService,
            ServerTrackerStore serverTrackerStore,
            TrackerProgressRepository trackerProgressRepository,
            HoneypotDetector honeypotDetector,
            PlayerSampleVerifier playerSampleVerifier,
            JdbcTemplate jdbcTemplate,
            TrackerStatsService trackerStatsService,
            @Value("${mc-utils.server-tracker.enabled:true}") boolean enabled,
            @Value("${mc-utils.server-tracker.refresh.enabled:true}") boolean refreshEnabled,
            @Value("${mc-utils.server-tracker.refresh.min-gap-hours:6}") long refreshMinGapHours,
            @Value("${mc-utils.server-tracker.refresh.chunk-size:2500}") int refreshChunkSize,
            @Value("${mc-utils.server-tracker.refresh.concurrent-fetches:200}") int refreshConcurrentFetches,
            @Value("${mc-utils.server-tracker.refresh.timeout-ms:5000}") int refreshTimeoutMs,
            @Value("${mc-utils.server-tracker.discovery.concurrency:20000}") int discoveryConcurrency,
            @Value("${mc-utils.server-tracker.discovery.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${mc-utils.server-tracker.verify.concurrency:200}") int verifyConcurrency,
            @Value("${mc-utils.server-tracker.verify.timeout-ms:5000}") int verifyTimeoutMs,
            @Value("${mc-utils.server-tracker.ports.discovery:25564,25565,25566,25567}") String discoveryPortsCsv,
            @Value("${mc-utils.server-tracker.ports.window:10}") int portWindow,
            @Value("${mc-utils.server-tracker.ports.max:65535}") int maxPort,
            @Value("${mc-utils.server-tracker.ports.probe-cap-per-ip:300}") int probeCapPerIp,
            @Value("${mc-utils.server-tracker.enqueue.batch-size:1000}") int enqueueBatchSize,
            @Value("${mc-utils.server-tracker.enqueue.rate-per-minute:3000}") long enqueueRatePerMinute,
            @Value("${mc-utils.server-tracker.progress-log-interval-seconds:60}") int progressLogIntervalSeconds,
            @Value("${mc-utils.server-tracker.ip.exclude-extra-cidrs:}") String excludeExtraCidrs,
            @Value("${mc-utils.server-tracker.ip.include-cidrs:}") String includeCidrs
    ) {
        this.playerSubmitService = playerSubmitService;
        this.serverTrackerStore = serverTrackerStore;
        this.trackerProgressRepository = trackerProgressRepository;
        this.honeypotDetector = honeypotDetector;
        this.playerSampleVerifier = playerSampleVerifier;
        this.jdbcTemplate = jdbcTemplate;
        this.trackerStatsService = trackerStatsService;
        this.enabled = enabled;
        this.refreshEnabled = refreshEnabled;
        this.refreshMinGapHours = refreshMinGapHours;
        this.refreshChunkSize = refreshChunkSize;
        this.refreshConcurrentFetches = refreshConcurrentFetches;
        this.refreshTimeoutMs = refreshTimeoutMs;
        this.discoveryConcurrency = discoveryConcurrency;
        this.connectTimeoutMs = connectTimeoutMs;
        this.verifyConcurrency = verifyConcurrency;
        this.verifyTimeoutMs = verifyTimeoutMs;
        this.discoveryPortsCsv = discoveryPortsCsv;
        this.portWindow = portWindow;
        this.maxPort = maxPort;
        this.probeCapPerIp = probeCapPerIp;
        this.enqueueBatchSize = enqueueBatchSize;
        this.enqueueRatePerMinute = enqueueRatePerMinute;
        this.progressLogIntervalSeconds = Math.max(10, progressLogIntervalSeconds);
        this.excludeExtraCidrs = splitCidrs(excludeExtraCidrs);
        this.includeCidrs = splitCidrs(includeCidrs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (enabled || refreshEnabled) {
            this.upkeepExecutor = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon(true).name("tracker-upkeep").factory()
            );
            // Store + harvest flush must run regardless of which loops are active: the refresh
            // cycle records snapshots even when the discovery campaign is disabled.
            upkeepExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (harvester != null) {
                        harvester.flush();
                    }
                    serverTrackerStore.flush();
                } catch (Exception e) {
                    log.warn("Tracker flush task failed", e);
                }
            }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        if (enabled) {
            startDiscovery();
        } else {
            log.info("Server tracker discovery disabled (mc-utils.server-tracker.enabled=false)");
        }
        startRefresher();
    }

    private void startDiscovery() {
        TrackerProgressRow stored = trackerProgressRepository.findById(TrackerProgressRow.SINGLETON_ID).orElse(null);
        long seed;
        if (stored != null && !TrackerProgressRow.State.COMPLETED.name().equals(stored.getState())) {
            seed = stored.getSeed();
        } else {
            seed = ThreadLocalRandom.current().nextLong();
            if (stored == null) {
                trackerProgressRepository.save(new TrackerProgressRow(
                        TrackerProgressRow.SINGLETON_ID, seed, 0, 0,
                        TrackerProgressRow.State.RUNNING.name(), Instant.now(), Instant.now()
                ));
            } else {
                stored.setSeed(seed);
                stored.setState(TrackerProgressRow.State.RUNNING.name());
                stored.setUpdatedAt(Instant.now());
                trackerProgressRepository.save(stored);
            }
        }

        this.space = new Ipv4Space(excludeExtraCidrs, includeCidrs, seed);
        if (stored != null && !TrackerProgressRow.State.COMPLETED.name().equals(stored.getState())) {
            space.restore(new Ipv4Space.Progress(seed, stored.getPermuted16Pos(), stored.getOffset24()));
        }
        this.metrics = MetricService.getMetric(ServerTrackerMetric.class);
        this.verifierExecutor = Executors.newFixedThreadPool(
                verifyConcurrency,
                Thread.ofPlatform().daemon(true).name("tracker-verify-", 0).factory()
        );

        this.harvester = new BufferedHarvester(
                playerSubmitService, metrics, enqueueBatchSize, enqueueRatePerMinute
        );
        ServerTrackerVerifier.ServerTrackerSink serverSink = snapshot -> {
            serversFound.incrementAndGet();
            serverTrackerStore.record(snapshot);
        };
        ServerTrackerVerifier verifier = new ServerTrackerVerifier(
                new JavaMinecraftServerPinger(), honeypotDetector, serverSink, harvester::harvest, playerSampleVerifier, metrics,
                portWindow, maxPort, probeCapPerIp, verifyTimeoutMs
        );
        this.discovery = new ServerTrackerDiscovery(
                space, (ip, port) -> verifierExecutor.submit(() -> verifier.verifyHost(ip, port)), metrics,
                discoveryPortsCsv, discoveryConcurrency, connectTimeoutMs
        );

        upkeepExecutor.scheduleAtFixedRate(() -> {
            try {
                persistProgress();
            } catch (Exception e) {
                log.warn("Tracker progress persist task failed", e);
            }
        }, PROGRESS_PERSIST_INTERVAL_SECONDS, PROGRESS_PERSIST_INTERVAL_SECONDS, TimeUnit.SECONDS);
        upkeepExecutor.scheduleAtFixedRate(this::logProgress, progressLogIntervalSeconds, progressLogIntervalSeconds, TimeUnit.SECONDS);

        this.discoveryThread = Thread.ofPlatform().daemon(true).name("server-tracker").start(discovery::run);
        log.info("Server tracker discovery started (scoped mode: {}, seed: {})", space.hasIncludeScope(), seed);
    }

    private void startRefresher() {
        if (!refreshEnabled) {
            log.info("Server tracker refresh cycle disabled (mc-utils.server-tracker.refresh.enabled=false)");
            return;
        }
        if (this.metrics == null) {
            this.metrics = MetricService.getMetric(ServerTrackerMetric.class);
        }
        this.refresher = new ServerTrackerRefresher(
                jdbcTemplate, new JavaMinecraftServerPinger(), honeypotDetector, serverTrackerStore, playerSampleVerifier, metrics,
                trackerStatsService::refresh, refreshChunkSize, refreshConcurrentFetches, refreshTimeoutMs, refreshMinGapHours
        );
        refresher.start();
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("Stopping server tracker...");
        if (discovery != null) {
            discovery.close();
        }
        if (refresher != null) {
            refresher.stop();
        }
        if (verifierExecutor != null) {
            verifierExecutor.shutdownNow();
        }
        if (upkeepExecutor != null) {
            upkeepExecutor.shutdownNow();
        }
        if (discoveryThread != null) {
            try {
                discoveryThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        persistProgress(true);
        log.info("Server tracker stopped");
    }

    private void persistProgress() {
        persistProgress(false);
    }

    /**
     * Periodic console summary of the scan. Logs once per tick only when progress moved, so a
     * wedged loop is visible as silence rather than noisy repetition.
     */
    private void logProgress() {
        if (discovery == null || space == null || harvester == null) {
            return;
        }
        if (discovery.isComplete()) {
            log.info("Server tracker completed: {} /24 subnets, servers found={}, players enqueued={}",
                    discovery.completed24s(), serversFound.get(), harvester.totalEnqueued());
            return;
        }
        long completed = discovery.completed24s();
        long now = System.nanoTime();
        if (completed == lastLogCompleted24s) {
            return; // no movement since the last tick
        }
        double minutes = (now - lastLogNanos) / 60_000_000_000.0;
        long perMinute = minutes > 0 ? (long) ((completed - lastLogCompleted24s) / minutes) : 0;
        this.lastLogCompleted24s = completed;
        this.lastLogNanos = now;

        long total = space.public24Count();
        if (total > 0) {
            double percent = completed * 100.0 / total;
            String eta = perMinute > 0 ? ", ETA " + formatEta(Duration.ofSeconds(Math.max(0, (total - completed) * 60 / perMinute))) : "";
            long probesPerSecond = perMinute * 254L * discovery.discoveryPortCount() / 60L;
            log.info("Server tracker progress: {} / {} /24 subnets ({}%), {} /24s/min, ~{} probes/s{}, servers found={}, players enqueued={}",
                    completed, total, String.format(Locale.ROOT, "%.1f", percent),
                    perMinute, probesPerSecond, eta, serversFound.get(), harvester.totalEnqueued());
        } else {
            log.info("Server tracker progress (scoped): {} /24 subnets, {} /24s/min, servers found={}, players enqueued={}",
                    completed, perMinute, serversFound.get(), harvester.totalEnqueued());
        }
    }

    private static String formatEta(Duration eta) {
        long days = eta.toDays();
        long hours = eta.toHoursPart();
        long minutes = eta.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private void persistProgress(boolean stopping) {
        if (space == null || discovery == null || !running.get() && !stopping) {
            return;
        }
        try {
            Ipv4Space.Progress cursor = discovery.cursor();
            int permuted16Pos = cursor == null ? 0 : cursor.permuted16Pos();
            int offset24 = cursor == null ? 0 : cursor.offset24();
            String state = discovery.isComplete()
                    ? TrackerProgressRow.State.COMPLETED.name()
                    : TrackerProgressRow.State.PAUSED.name();
            TrackerProgressRow stored = trackerProgressRepository.findById(TrackerProgressRow.SINGLETON_ID).orElseGet(() -> {
                TrackerProgressRow created = new TrackerProgressRow(
                        TrackerProgressRow.SINGLETON_ID, space.seed(), 0, 0,
                        TrackerProgressRow.State.RUNNING.name(), Instant.now(), Instant.now()
                );
                return trackerProgressRepository.save(created);
            });
            stored.setSeed(space.seed());
            stored.setPermuted16Pos(permuted16Pos);
            stored.setOffset24(offset24);
            stored.setState(state);
            stored.setUpdatedAt(Instant.now());
            trackerProgressRepository.save(stored);
        } catch (Exception e) {
            log.debug("Failed to persist tracker progress: {}", e.toString());
        }
    }

    private static List<String> splitCidrs(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Buffers harvested players and flushes them to the submit queue in batches, rate-capped so
     * the Redis queue cannot outrun the shared Mojang budget. Honeypot-flagged hosts are skipped
     * by the verifier, so nothing from them ever reaches this queue.
     */
    static final class BufferedHarvester implements ServerTrackerVerifier.PlayerHarvester {
        private final PlayerSubmitService submitService;
        private final ServerTrackerMetric metrics;
        private final int batchSize;
        private final long minFlushIntervalNanos;
        private final List<HoneypotDetector.SampleEntry> buffer = new ArrayList<>();
        private long lastFlush;
        private long totalEnqueued;

        BufferedHarvester(PlayerSubmitService submitService, ServerTrackerMetric metrics, int batchSize,
                          long enqueueRatePerMinute) {
            this.submitService = submitService;
            this.metrics = metrics;
            this.batchSize = batchSize;
            this.minFlushIntervalNanos = TimeUnit.MINUTES.toNanos(1) / Math.max(1, enqueueRatePerMinute);
        }

        @Override
        public synchronized int harvest(List<HoneypotDetector.SampleEntry> players) {
            buffer.addAll(players);
            if (buffer.size() >= batchSize) {
                return flushLocked();
            }
            return 0;
        }

        synchronized int flush() {
            return flushLocked();
        }

        private int flushLocked() {
            if (buffer.isEmpty()) {
                return 0;
            }
            long now = System.nanoTime();
            if (now - lastFlush < minFlushIntervalNanos) {
                return 0;
            }
            List<HoneypotDetector.SampleEntry> batch = List.copyOf(buffer);
            buffer.clear();
            lastFlush = now;
            List<String> uuids = batch.stream().map(e -> e.uuid().toString()).toList();
            if (uuids.isEmpty()) {
                return 0;
            }
            int enqueued = submitService.submitPlayers(uuids, SUBMITTED_BY.toString());
            this.totalEnqueued += enqueued;
            if (metrics != null) {
                metrics.recordPlayersEnqueued(enqueued);
            }
            return enqueued;
        }

        synchronized long totalEnqueued() {
            return totalEnqueued;
        }
    }
}