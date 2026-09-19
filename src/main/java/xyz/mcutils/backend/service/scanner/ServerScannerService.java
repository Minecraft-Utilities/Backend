package xyz.mcutils.backend.service.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.impl.scanner.ServerScannerMetric;
import xyz.mcutils.backend.model.persistence.postgres.ScanProgressRow;
import xyz.mcutils.backend.model.persistence.postgres.ScannedServerRow;
import xyz.mcutils.backend.repository.postgres.ScanProgressRepository;
import xyz.mcutils.backend.repository.postgres.ServerScannerRepository;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerSubmitService;
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
 * Orchestrates the internet server scanner. Opt-in via {@code mc-utils.server-scanner.enabled};
 * when disabled the service does nothing. On start it restores the last persisted position
 * (seed + /16 + /24 offset), runs the NIO discovery sweep, verifies open hosts with the port
 * walk, and feeds harvested players into {@link PlayerSubmitService} attributed to ImFascinated.
 */
@Service
@Slf4j
public class ServerScannerService {

    /**
     * ImFascinated's UUID: all scanner harvests are attributed to this account so
     * {@code TopSubmittedPlayers} counts them there.
     */
    public static final UUID SUBMITTED_BY = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48");

    private static final long PROGRESS_PERSIST_INTERVAL_SECONDS = 30;
    private static final long FLUSH_INTERVAL_SECONDS = 2;

    private final PlayerSubmitService playerSubmitService;
    private final ServerScannerRepository serverScannerRepository;
    private final ScanProgressRepository scanProgressRepository;
    private final HoneypotDetector honeypotDetector;

    private final boolean enabled;
    private final int verifyConcurrency;
    private final int enqueueBatchSize;
    private final long enqueueRatePerMinute;
    private final int surgeWindow;
    private final int surgeMaxFlagged;
    private final long surgeCooldownSeconds;
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

    private ServerDiscoveryScanner discovery;
    private Ipv4Space space;
    private ServerScannerMetric metrics;
    private BufferedHarvester harvester;
    private ExecutorService verifierExecutor;
    private ScheduledExecutorService upkeepExecutor;
    private Thread discoveryThread;

    public ServerScannerService(
            PlayerSubmitService playerSubmitService,
            ServerScannerRepository serverScannerRepository,
            ScanProgressRepository scanProgressRepository,
            HoneypotDetector honeypotDetector,
            @Value("${mc-utils.server-scanner.enabled:false}") boolean enabled,
            @Value("${mc-utils.server-scanner.discovery.concurrency:50000}") int discoveryConcurrency,
            @Value("${mc-utils.server-scanner.discovery.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${mc-utils.server-scanner.verify.concurrency:500}") int verifyConcurrency,
            @Value("${mc-utils.server-scanner.verify.timeout-ms:5000}") int verifyTimeoutMs,
            @Value("${mc-utils.server-scanner.ports.discovery:25564,25565,25566,25567,25568,25569,25570,25575,25580,25585,25590,25600}") String discoveryPortsCsv,
            @Value("${mc-utils.server-scanner.ports.window:10}") int portWindow,
            @Value("${mc-utils.server-scanner.ports.max:65535}") int maxPort,
            @Value("${mc-utils.server-scanner.ports.probe-cap-per-ip:500}") int probeCapPerIp,
            @Value("${mc-utils.server-scanner.enqueue.batch-size:1000}") int enqueueBatchSize,
            @Value("${mc-utils.server-scanner.enqueue.rate-per-minute:3000}") long enqueueRatePerMinute,
            @Value("${mc-utils.server-scanner.honeypot.surge-window:100}") int surgeWindow,
            @Value("${mc-utils.server-scanner.honeypot.surge-max-flagged:10}") int surgeMaxFlagged,
            @Value("${mc-utils.server-scanner.honeypot.surge-cooldown-seconds:300}") long surgeCooldownSeconds,
            @Value("${mc-utils.server-scanner.progress-log-interval-seconds:60}") int progressLogIntervalSeconds,
            @Value("${mc-utils.server-scanner.ip.exclude-extra-cidrs:}") String excludeExtraCidrs,
            @Value("${mc-utils.server-scanner.ip.include-cidrs:}") String includeCidrs
    ) {
        this.playerSubmitService = playerSubmitService;
        this.serverScannerRepository = serverScannerRepository;
        this.scanProgressRepository = scanProgressRepository;
        this.honeypotDetector = honeypotDetector;
        this.enabled = enabled;
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
        this.surgeWindow = surgeWindow;
        this.surgeMaxFlagged = surgeMaxFlagged;
        this.surgeCooldownSeconds = surgeCooldownSeconds;
        this.progressLogIntervalSeconds = Math.max(10, progressLogIntervalSeconds);
        this.excludeExtraCidrs = splitCidrs(excludeExtraCidrs);
        this.includeCidrs = splitCidrs(includeCidrs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled) {
            log.info("Server scanner disabled (mc-utils.server-scanner.enabled=false)");
            return;
        }
        if (running.get()) {
            return;
        }

        ScanProgressRow stored = scanProgressRepository.findById(ScanProgressRow.SINGLETON_ID).orElse(null);
        long seed;
        if (stored != null && !ScanProgressRow.State.COMPLETED.name().equals(stored.getState())) {
            seed = stored.getSeed();
        } else {
            seed = ThreadLocalRandom.current().nextLong();
            if (stored == null) {
                scanProgressRepository.save(new ScanProgressRow(
                        ScanProgressRow.SINGLETON_ID, seed, 0, 0,
                        ScanProgressRow.State.RUNNING.name(), Instant.now(), Instant.now()
                ));
            } else {
                stored.setSeed(seed);
                stored.setState(ScanProgressRow.State.RUNNING.name());
                stored.setUpdatedAt(Instant.now());
                scanProgressRepository.save(stored);
            }
        }

        this.space = new Ipv4Space(excludeExtraCidrs, includeCidrs, seed);
        if (stored != null && !ScanProgressRow.State.COMPLETED.name().equals(stored.getState())) {
            space.restore(new Ipv4Space.Progress(seed, stored.getPermuted16Pos(), stored.getOffset24()));
        }
        this.metrics = MetricService.getMetric(ServerScannerMetric.class);
        this.verifierExecutor = Executors.newFixedThreadPool(
                verifyConcurrency,
                Thread.ofPlatform().daemon(true).name("scanner-verify-", 0).factory()
        );
        this.upkeepExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("scanner-upkeep").factory()
        );

        HoneypotSurgeGuard surgeGuard = new HoneypotSurgeGuard(surgeWindow, surgeMaxFlagged, surgeCooldownSeconds);
        this.harvester = new BufferedHarvester(
                playerSubmitService, metrics, enqueueBatchSize, enqueueRatePerMinute, surgeGuard
        );
        ServerScanVerifier.ScannedServerSink serverSink = (ip, port, sampleCount, honeypot) -> {
            if (surgeGuard.report(honeypot) && metrics != null) {
                metrics.recordHarvestPause();
                log.warn("Honeypot surge detected (>={} of last {} verified servers flagged), harvesting paused for {}s",
                        surgeMaxFlagged, surgeWindow, surgeCooldownSeconds);
            }
            persistScannedServer(ip, port, sampleCount, honeypot);
        };
        ServerScanVerifier verifier = new ServerScanVerifier(
                new JavaMinecraftServerPinger(), honeypotDetector, serverSink, harvester::harvest, metrics,
                portWindow, maxPort, probeCapPerIp, verifyTimeoutMs
        );
        this.discovery = new ServerDiscoveryScanner(
                space, (ip, port) -> verifierExecutor.submit(() -> verifier.verifyHost(ip, port)), metrics,
                discoveryPortsCsv, discoveryConcurrency, connectTimeoutMs
        );

        upkeepExecutor.scheduleAtFixedRate(() -> {
            try {
                harvester.flush();
            } catch (Exception e) {
                log.warn("Scanner flush task failed", e);
            }
        }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        upkeepExecutor.scheduleAtFixedRate(() -> {
            try {
                persistProgress();
            } catch (Exception e) {
                log.warn("Scanner progress persist task failed", e);
            }
        }, PROGRESS_PERSIST_INTERVAL_SECONDS, PROGRESS_PERSIST_INTERVAL_SECONDS, TimeUnit.SECONDS);
        upkeepExecutor.scheduleAtFixedRate(this::logProgress, progressLogIntervalSeconds, progressLogIntervalSeconds, TimeUnit.SECONDS);

        running.set(true);
        this.discoveryThread = Thread.ofPlatform().daemon(true).name("server-scanner").start(discovery::run);
        log.info("Server scanner started (scoped mode: {}, seed: {})", space.hasIncludeScope(), seed);
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("Stopping server scanner...");
        if (discovery != null) {
            discovery.close();
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
        log.info("Server scanner stopped");
    }

    private void persistScannedServer(String ip, int port, int sampleCount, boolean honeypot) {
        serversFound.incrementAndGet();
        try {
            Instant now = Instant.now();
            Optional<ScannedServerRow> existing = serverScannerRepository.findByIpAndPort(ip, port);
            if (existing.isPresent()) {
                ScannedServerRow row = existing.get();
                row.setLastSeen(now);
                row.setSampleCount(sampleCount);
                row.setHoneypot(row.isHoneypot() || honeypot);
                serverScannerRepository.save(row);
            } else {
                serverScannerRepository.save(new ScannedServerRow(ip, port, now, now, sampleCount, honeypot));
            }
        } catch (Exception e) {
            log.debug("Failed to persist scanned server {}:{}: {}", ip, port, e.toString());
        }
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
            log.info("Server scanner completed: {} /24 subnets, servers found={}, players enqueued={}",
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
            log.info("Server scanner progress: {} / {} /24 subnets ({}%), {} /24s/min, ~{} probes/s{}, servers found={}, players enqueued={}",
                    completed, total, String.format(Locale.ROOT, "%.1f", percent),
                    perMinute, probesPerSecond, eta, serversFound.get(), harvester.totalEnqueued());
        } else {
            log.info("Server scanner progress (scoped): {} /24 subnets, {} /24s/min, servers found={}, players enqueued={}",
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
                    ? ScanProgressRow.State.COMPLETED.name()
                    : ScanProgressRow.State.PAUSED.name();
            ScanProgressRow stored = scanProgressRepository.findById(ScanProgressRow.SINGLETON_ID).orElseGet(() -> {
                ScanProgressRow created = new ScanProgressRow(
                        ScanProgressRow.SINGLETON_ID, space.seed(), 0, 0,
                        ScanProgressRow.State.RUNNING.name(), Instant.now(), Instant.now()
                );
                return scanProgressRepository.save(created);
            });
            stored.setSeed(space.seed());
            stored.setPermuted16Pos(permuted16Pos);
            stored.setOffset24(offset24);
            stored.setState(state);
            stored.setUpdatedAt(Instant.now());
            scanProgressRepository.save(stored);
        } catch (Exception e) {
            log.debug("Failed to persist scanner progress: {}", e.toString());
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
     * the Redis queue cannot outrun the shared Mojang budget. Harvesting pauses while the
     * honeypot surge guard is active.
     */
    static final class BufferedHarvester implements ServerScanVerifier.PlayerHarvester {
        private final PlayerSubmitService submitService;
        private final ServerScannerMetric metrics;
        private final int batchSize;
        private final long minFlushIntervalNanos;
        private final HoneypotSurgeGuard surgeGuard;
        private final List<HoneypotDetector.SampleEntry> buffer = new ArrayList<>();
        private long lastFlush;
        private long totalEnqueued;

        BufferedHarvester(PlayerSubmitService submitService, ServerScannerMetric metrics, int batchSize,
                          long enqueueRatePerMinute, HoneypotSurgeGuard surgeGuard) {
            this.submitService = submitService;
            this.metrics = metrics;
            this.batchSize = batchSize;
            this.minFlushIntervalNanos = TimeUnit.MINUTES.toNanos(1) / Math.max(1, enqueueRatePerMinute);
            this.surgeGuard = surgeGuard;
        }

        @Override
        public synchronized int harvest(List<HoneypotDetector.SampleEntry> players) {
            if (surgeGuard.paused()) {
                return 0;
            }
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

    /**
     * Sliding window of the most recent verified-server honeypot verdicts. When the flagged
     * ratio crosses the threshold, harvesting pauses for the cooldown (set-and-forget: the
     * window keeps sliding and the guard re-arms automatically).
     */
    static final class HoneypotSurgeGuard {
        private final boolean[] ring;
        private final int maxFlagged;
        private final long cooldownNanos;
        private int head;
        private int size;
        private long pausedUntil;

        HoneypotSurgeGuard(int window, int maxFlagged, long cooldownSeconds) {
            this.ring = new boolean[Math.max(1, window)];
            this.maxFlagged = maxFlagged;
            this.cooldownNanos = TimeUnit.SECONDS.toNanos(cooldownSeconds);
        }

        synchronized boolean report(boolean flagged) {
            ring[head] = flagged;
            head = (head + 1) % ring.length;
            if (size < ring.length) {
                size++;
            }
            int count = 0;
            for (int i = 0; i < size; i++) {
                if (ring[i]) {
                    count++;
                }
            }
            if (count >= maxFlagged && !paused()) {
                pausedUntil = System.nanoTime() + cooldownNanos;
                return true;
            }
            return false;
        }

        boolean paused() {
            return System.nanoTime() < pausedUntil;
        }
    }
}