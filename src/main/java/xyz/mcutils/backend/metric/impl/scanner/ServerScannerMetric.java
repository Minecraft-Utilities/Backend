package xyz.mcutils.backend.metric.impl.scanner;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Counters/gauges for the internet server scanner. {@code ip_probes_total} counts TCP connect
 * probes in the discovery stage; {@code servers_verified_total} counts hosts that answered a
 * valid Java status ping; {@code sample_entries_dropped_total} breaks down why harvested sample
 * entries were rejected (honeypot signals); {@code players_enqueued_total} is the number of
 * players actually handed to the submit queue.
 */
public class ServerScannerMetric extends Metric<ServerScannerMetric.Holder> {

    /** Snapshot shown by the progress gauge; updated by the scanner loop, read by the scrape. */
    private static final AtomicReference<Long> SCAN_PROGRESS = new AtomicReference<>(0L);

    public ServerScannerMetric() {
        super(new Holder(
                Counter.builder()
                        .name("server_scanner_ip_probes_total")
                        .help("TCP connect probes issued by the discovery stage")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_connect_open_total")
                        .help("Discovery probes whose TCP connect succeeded")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_servers_verified_total")
                        .help("Hosts that answered a valid Java status ping")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_ports_probed_walk_total")
                        .help("Port-walk probes issued beyond the base port")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_players_harvested_total")
                        .help("Sample entries that passed the structural filters")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_players_enqueued_total")
                        .help("Players handed to the submit queue")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_sample_entries_dropped_total")
                        .help("Sample entries rejected by the honeypot filters")
                        .labelNames("reason")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_honeypot_servers_flagged_total")
                        .help("Verified hosts flagged as honeypots")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_honeypot_fingerprints_blocked_total")
                        .help("Sample fingerprints recognized as honeypot farms")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("server_scanner_harvest_pauses_total")
                        .help("Times harvesting was paused by the honeypot surge guard")
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("server_scanner_progress_24s")
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

    public void recordHarvestPause() {
        getValue().harvestPauses.inc();
    }

    public static void updateProgress(long completed24s) {
        SCAN_PROGRESS.set(completed24s);
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
            Counter harvestPauses,
            GaugeWithCallback progress
    ) {}
}