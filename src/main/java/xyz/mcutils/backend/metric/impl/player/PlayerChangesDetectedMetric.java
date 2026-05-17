package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Counter for skin, cape, and username changes detected during player updates.
 * Compare rate to accounts_updated_total to measure change hit rate under weighted refresh.
 */
public class PlayerChangesDetectedMetric extends Metric<PlayerChangesDetectedMetric.Holder> {

    public PlayerChangesDetectedMetric() {
        super(new Holder(Counter.builder()
                .name("player_changes_detected_total")
                .help("Total skin, cape, and username changes detected during player updates")
                .register(MetricService.REGISTRY)));
    }

    public void inc(long n) {
        if (n > 0) {
            getValue().counter.inc(n);
        }
    }

    public record Holder(Counter counter) {}
}
