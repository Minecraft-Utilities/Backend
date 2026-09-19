package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Counter of detected player changes during updates, labeled by change type.
 * Compare per-type rates against {@code accounts_updated_total} (updates) to measure
 * change hit rate under weighted refresh.
 */
public class PlayerChangesDetectedMetric extends Metric<PlayerChangesDetectedMetric.Holder> {

    public static final String SKIN = "skin";
    public static final String CAPE = "cape";
    public static final String USERNAME = "username";

    public PlayerChangesDetectedMetric() {
        super(new Holder(Counter.builder()
                .name("player_changes_detected_total")
                .help("Detected player changes by type (skin, cape, username) during player updates")
                .labelNames("type")
                .register(MetricService.REGISTRY)));
    }

    public void record(String type) {
        getValue().counter.labelValues(type).inc();
    }

    public record Holder(Counter counter) {}
}