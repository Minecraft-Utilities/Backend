package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

import java.time.Duration;

/**
 * Counters for the background player refresh loop.
 * Compare {@code mojang_lookups} to {@code accounts_updated_total} to see if the loop is
 * stalling before HTTP (low lookups) or after (high lookups, low updates).
 */
public class PlayerRefreshMetric extends Metric<PlayerRefreshMetric.Holder> {

    public PlayerRefreshMetric() {
        super(new Holder(
                Counter.builder()
                        .name("player_refresh_mojang_lookups_total")
                        .help("Mojang profile lookups started by the background refresh loop")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("player_refresh_persist_total")
                        .help("Player rows successfully persisted after a background refresh Mojang lookup")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("player_refresh_interval_seconds")
                        .help("Computed adaptive refresh interval after a successful player update")
                        .classicUpperBounds(1200, 3600, 7200, 14400, 28800, 86400, 172800, 604800, 1209600)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordMojangLookup() {
        getValue().mojangLookups.inc();
    }

    public void recordPersist(long count) {
        if (count > 0) {
            getValue().persist.inc(count);
        }
    }

    public void recordInterval(Duration interval) {
        getValue().intervalSeconds.observe(interval.toMillis() / 1000.0);
    }

    public record Holder(Counter mojangLookups, Counter persist, Histogram intervalSeconds) {}
}
