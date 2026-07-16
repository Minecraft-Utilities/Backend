package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for the background player refresh loop.
 * Compare {@code mojang_lookups} to {@code accounts_updated_total} to see if the loop is
 * stalling before HTTP (low lookups) or after (high lookups, low updates).
 */
public class PlayerRefreshMetric extends Metric<PlayerRefreshMetric.Holder> {

    private final AtomicLong overdueCount = new AtomicLong();

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
                        .classicUpperBounds(1200, 3600, 7200, 14400, 28800, 86400)
                        .register(MetricService.REGISTRY)
        ));
        GaugeWithCallback.builder()
                .name("player_refresh_overdue_total")
                .help("Players with next_refresh_at in the past, sampled each refresh loop iteration")
                .callback(callback -> callback.call(overdueCount.get()))
                .register(MetricService.REGISTRY);
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

    public void recordOverdueCount(long count) {
        overdueCount.set(count);
    }

    public record Holder(Counter mojangLookups, Counter persist, Histogram intervalSeconds) {}
}
