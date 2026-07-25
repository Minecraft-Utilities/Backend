package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.discovery.UsernameDiscoveryStrategy;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.UsernameDiscoveryService;

/**
 * Metrics for the background username discovery pipeline.
 */
public class UsernameDiscoveryMetric extends Metric<UsernameDiscoveryMetric.Holder> {
    public static final String STRATEGY_UNKNOWN = "unknown";

    public enum SkipReason {
        ALREADY_SEEN("already_seen"),
        ALREADY_TRACKED("already_tracked"),
        INVALID("invalid"),
        DUPLICATE_IN_BATCH("duplicate_in_batch");

        private final String label;

        SkipReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum BulkLookupOutcome {
        SUCCESS("success"),
        RATE_LIMITED("rate_limited"),
        TIMED_OUT("timed_out"),
        ERROR("error");

        private final String label;

        BulkLookupOutcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public UsernameDiscoveryMetric(UsernameDiscoveryService usernameDiscoveryService) {
        super(new Holder(
                Counter.builder()
                        .name("username_discovery_candidates_generated_total")
                        .help("Username candidates produced by strategy before filtering")
                        .labelNames("strategy")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_candidates_enqueued_total")
                        .help("Username candidates added to the discovery queue")
                        .labelNames("strategy")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_candidates_skipped_total")
                        .help("Username candidates dropped before enqueue")
                        .labelNames("reason")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_candidates_checked_total")
                        .help("Username candidates checked via Mojang bulk lookup")
                        .labelNames("strategy")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_hits_total")
                        .help("Username candidates that resolved to a Mojang profile")
                        .labelNames("strategy")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_submit_enqueued_total")
                        .help("UUIDs forwarded to the player submit ingest queue after a discovery hit")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_bulk_lookups_total")
                        .help("Mojang bulk username lookup requests by outcome")
                        .labelNames("outcome")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_producer_players_scanned_total")
                        .help("Players scanned by the discovery producer")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("username_discovery_producer_cycles_total")
                        .help("Completed producer passes over the player table")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("username_discovery_bulk_lookup_duration_milliseconds")
                        .help("Duration of Mojang bulk username lookup requests")
                        .classicUpperBounds(25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("username_discovery_producer_duration_milliseconds")
                        .help("Duration of a producer batch (scan players and enqueue candidates)")
                        .classicUpperBounds(50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000)
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("username_discovery_queue_size")
                        .help("Pending username candidates in the discovery queue")
                        .callback(callback -> callback.call(usernameDiscoveryService.getQueueSize()))
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("username_discovery_seen_set_size")
                        .help("Usernames already checked by discovery (Redis set cardinality)")
                        .callback(callback -> callback.call(usernameDiscoveryService.getSeenSetSize()))
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordGenerated(UsernameDiscoveryStrategy strategy, long count) {
        if (count > 0) {
            getValue().generated.labelValues(strategyLabel(strategy)).inc(count);
        }
    }

    public void recordEnqueued(UsernameDiscoveryStrategy strategy, long count) {
        if (count > 0) {
            getValue().enqueued.labelValues(strategyLabel(strategy)).inc(count);
        }
    }

    public void recordSkipped(SkipReason reason, long count) {
        if (count > 0) {
            getValue().skipped.labelValues(reason.label()).inc(count);
        }
    }

    public void recordChecked(UsernameDiscoveryStrategy strategy, long count) {
        if (count > 0) {
            getValue().checked.labelValues(strategyLabel(strategy)).inc(count);
        }
    }

    public void recordHit(UsernameDiscoveryStrategy strategy) {
        getValue().hits.labelValues(strategyLabel(strategy)).inc();
    }

    public void recordSubmitEnqueued(long count) {
        if (count > 0) {
            getValue().submitEnqueued.inc(count);
        }
    }

    public void recordBulkLookup(BulkLookupOutcome outcome, long durationMs) {
        getValue().bulkLookups.labelValues(outcome.label()).inc();
        if (outcome == BulkLookupOutcome.SUCCESS) {
            getValue().bulkLookupDuration.observe(durationMs);
        }
    }

    public void recordProducerPlayersScanned(long count) {
        if (count > 0) {
            getValue().producerPlayersScanned.inc(count);
        }
    }

    public void recordProducerCycle() {
        getValue().producerCycles.inc();
    }

    public void recordProducerDuration(long durationMs) {
        getValue().producerDuration.observe(durationMs);
    }

    private static String strategyLabel(UsernameDiscoveryStrategy strategy) {
        return strategy != null ? strategy.metricLabel() : STRATEGY_UNKNOWN;
    }

    public record Holder(
            Counter generated,
            Counter enqueued,
            Counter skipped,
            Counter checked,
            Counter hits,
            Counter submitEnqueued,
            Counter bulkLookups,
            Counter producerPlayersScanned,
            Counter producerCycles,
            Histogram bulkLookupDuration,
            Histogram producerDuration,
            GaugeWithCallback queueSize,
            GaugeWithCallback seenSetSize
    ) {}
}
