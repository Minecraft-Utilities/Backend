package xyz.mcutils.backend.metric.impl.flush;

import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks how long each entity-type flush takes in {@link xyz.mcutils.backend.service.FlushScheduler}.
 * Label {@code entity_type} is one of: {@code player}, {@code skin}, {@code cape}.
 */
public class FlushDurationMetric extends Metric<FlushDurationMetric.Holder> {
    public FlushDurationMetric() {
        super(new Holder(
                Histogram.builder()
                        .name("flush_duration_milliseconds")
                        .help("Time taken to flush dirty cache entries to MongoDB, by entity type")
                        .labelNames("entity_type")
                        .classicUpperBounds(50, 100, 250, 500, 1000, 2500, 5000, 15000, 30000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(String entityType, long durationMs) {
        getValue().histogram.labelValues(entityType).observe(durationMs);
    }

    public record Holder(Histogram histogram) {}
}
