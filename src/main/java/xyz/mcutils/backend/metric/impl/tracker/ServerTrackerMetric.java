package xyz.mcutils.backend.metric.impl.tracker;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Counters/gauges for the internet server tracker. {@code ip_probes_total} counts TCP connect
 * probes in the discovery stage; {@code servers_verified_total} counts hosts that answered a
 * valid Java status ping; {@code sample_entries_dropped_total} breaks down why harvested sample
 * entries were rejected (honeypot signals); {@code players_enqueued_total} is the number of
 * players actually handed to the submit queue.
 */
public class ServerTrackerMetric extends Metric<ServerTrackerMetric.Holder> {

    /** Snapshot shown by the progress gauge; updated by the tracker loop, read by the scrape. */
    private static final AtomicReference<Long> SCAN_PROGRESS = new AtomicReference<>(0L);

    /** Snapshot of the tracked-server count; fed by {@code TrackerStatsService} on refresh. */
    private static final AtomicReference<Long> TRACKED_SERVERS = new AtomicReference<>(0L);

    public ServerTrackerMetric() {
        super(new Holder(
                Counter.builder()
                        .name("server_tracker_ip_probes_total")
                        .help("TCP connect probes issued by the discovery stage")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_connect_open_total")
                        .help("Discovery probes whose TCP connect succeeded")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_servers_verified_total")
                        .help("Hosts that answered a valid Java status ping")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_ports_probed_walk_total")
                        .help("Port-walk probes issued beyond the base port")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_players_harvested_total")
                        .help("Sample entries that passed the structural filters")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_players_enqueued_total")
                        .help("Players handed to the submit queue")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_sample_entries_dropped_total")
                        .help("Sample entries rejected by the honeypot filters")
                        .labelNames("reason")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_honeypot_servers_flagged_total")
                        .help("Verified hosts flagged as honeypots")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_honeypot_fingerprints_blocked_total")
                        .help("Sample fingerprints recognized as honeypot farms")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_players_seen_first_total")
                        .help("Player-history rows inserted (players seen on a tracked server for the first time)")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_geo_lookup_failures_total")
                        .help("MaxMind geo lookups that failed (columns left NULL)")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_refresh_pings_total")
                        .help("Status pings issued by the refresh cycle")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_refresh_persisted_total")
                        .help("Refresh-cycle successes persisted to the store")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_tracker_refresh_failed_total")
                        .help("Refresh-cycle ping failures by reason")
                        .labelNames("reason")
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("server_tracker_tracked_servers")
                        .help("Tracked servers currently in tracker_servers")
                        .callback(callback -> callback.call(TRACKED_SERVERS.get()))
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("server_tracker_progress_24s")
                        .help("Number of /24 subnets completed since the scan started")
                        .callback(callback -> callback.call(SCAN_PROGRESS.get()))
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordProbe() {
        getValue().ipProbes.inc();
    }

    public void recordConnectOpen() {
        getValue().connectOpen.inc();
    }

    public void recordServerVerified() {
        getValue().serversVerified.inc();
    }

    public void recordWalkProbe() {
        getValue().walkProbes.inc();
    }

    public void recordPlayersHarvested(long count) {
        getValue().playersHarvested.inc(count);
    }

    public void recordPlayersEnqueued(long count) {
        if (count > 0) {
            getValue().playersEnqueued.inc(count);
        }
    }

    public void recordSampleDropped(String reason) {
        getValue().sampleDropped.labelValues(reason).inc();
    }

    public void recordHoneypotServer() {
        getValue().honeypotServers.inc();
    }

    public void recordHoneypotFingerprintBlocked() {
        getValue().honeypotFingerprints.inc();
    }

    public void recordPlayersSeenFirst(long count) {
        getValue().playersSeenFirst.inc(count);
    }

    public void recordGeoLookupFailure() {
        getValue().geoLookupFailures.inc();
    }

    public void recordRefreshPing() {
        getValue().refreshPings.inc();
    }

    public void recordRefreshPersisted(int count) {
        getValue().refreshPersisted.inc(count);
    }

    public void recordRefreshFailed(String reason) {
        getValue().refreshFailed.labelValues(reason).inc();
    }

    public static void updateProgress(long completed24s) {
        SCAN_PROGRESS.set(completed24s);
    }

    public static void updateTrackedServers(long count) {
        TRACKED_SERVERS.set(count);
    }

    public record Holder(
            Counter ipProbes,
            Counter connectOpen,
            Counter serversVerified,
            Counter walkProbes,
            Counter playersHarvested,
            Counter playersEnqueued,
            Counter sampleDropped,
            Counter honeypotServers,
            Counter honeypotFingerprints,
            Counter playersSeenFirst,
            Counter geoLookupFailures,
            Counter refreshPings,
            Counter refreshPersisted,
            Counter refreshFailed,
            GaugeWithCallback trackedServers,
            GaugeWithCallback progress
    ) {}
}