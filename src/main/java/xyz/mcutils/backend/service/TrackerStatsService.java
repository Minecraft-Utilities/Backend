package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cached public statistics of the tracked-server dataset, served entirely from memory: requests
 * never touch the database (the {@code COUNT(DISTINCT player_uuid)} over tracker_player_history is far
 * too expensive for per-request work).
 * <p>
 * Follows the {@code StatisticsService} pattern: counters are loaded in memory at
 * {@code ApplicationReadyEvent} via {@code Main.EXECUTOR} (repositories must not be touched in
 * {@code @PostConstruct} — late singleton resolution on virtual threads deadlocks startup), then
 * recomputed on a fixed schedule and after every refresh-cycle chunk.
 */
@Service
@Slf4j
public class TrackerStatsService {

    private static final TrackerStatsResponse EMPTY =
            new TrackerStatsResponse(0, 0, 0, Map.of(), Map.of(), Map.of());

    /**
     * Skew allowed between a player's {@code last_seen} and the server's {@code last_updated}
     * for the player to count as verified online. The store flush window spans seconds to a
     * couple of minutes, while the refresh cycle re-samples each server only every
     * {@code refresh.min-gap-hours} — so this grace covers batch skew without keeping departed
     * players counted (they drop at the first refresh whose sample omits them).
     */
    private static final int SAMPLE_GRACE_SECONDS = 120;

    private final ServerTrackerRepository serverTrackerRepository;
    private final PlayerHistoryRepository playerHistoryRepository;
    private final boolean trackingEnabled;
    private final long refreshSeconds;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("tracker-stats").factory());

    private volatile TrackerStatsResponse snapshot = EMPTY;

    public TrackerStatsService(
            ServerTrackerRepository serverTrackerRepository,
            PlayerHistoryRepository playerHistoryRepository,
            @Value("${mc-utils.server-tracker.tracking.enabled:true}") boolean trackingEnabled,
            @Value("${mc-utils.server-tracker.stats.refresh-seconds:300}") long refreshSeconds
    ) {
        this.serverTrackerRepository = serverTrackerRepository;
        this.playerHistoryRepository = playerHistoryRepository;
        this.trackingEnabled = trackingEnabled;
        this.refreshSeconds = Math.max(10, refreshSeconds);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // Load once asynchronously; must not block bean startup (see StatisticsService).
        CompletableFuture.supplyAsync(this::load, Main.EXECUTOR).thenAccept(this::publish);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                refresh();
            } catch (Exception e) {
                log.warn("Tracker stats scheduled refresh failed: {}", e.toString());
            }
        }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    /**
     * Recomputes the stats snapshot. Skipped while another refresh is in flight and when
     * tracking is disabled. Called by the scheduler and by the refresh cycle after each chunk.
     */
    public void refresh() {
        if (!trackingEnabled || !refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            publish(load());
        } catch (Exception e) {
            log.warn("Failed to refresh tracker stats: {}", e.toString());
        } finally {
            refreshing.set(false);
        }
    }

    public TrackerStatsResponse getStats() {
        return snapshot;
    }

    private TrackerStatsResponse load() {
        long trackedServers = serverTrackerRepository.countTrackedServers();
        long trackedPlayers = playerHistoryRepository.countDistinctPlayers();
        long verifiedOnlinePlayers = serverTrackerRepository.countVerifiedOnlinePlayers(SAMPLE_GRACE_SECONDS);
        return new TrackerStatsResponse(
                trackedServers,
                trackedPlayers,
                verifiedOnlinePlayers,
                toCountMap(serverTrackerRepository.topCountries(PageRequest.of(0, 10))),
                toCountMap(serverTrackerRepository.topPlatforms(PageRequest.of(0, 10))),
                toCountMap(serverTrackerRepository.topProtocols(PageRequest.of(0, 10)))
        );
    }

    private void publish(TrackerStatsResponse stats) {
        this.snapshot = stats;
        ServerTrackerMetric.updateTrackedServers(stats.trackedServers());
    }

    private static Map<String, Long> toCountMap(java.util.List<ServerTrackerRepository.Breakdown> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (ServerTrackerRepository.Breakdown row : rows) {
            map.put(row.getGroupKey(), row.getTotal());
        }
        return map;
    }
}