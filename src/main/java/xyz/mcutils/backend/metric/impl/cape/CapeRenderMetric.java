package xyz.mcutils.backend.metric.impl.cape;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks cape render storage cache hits/misses and render duration for cache misses.
 */
public class CapeRenderMetric extends Metric<CapeRenderMetric.Holder> {
    public enum Result {
        CACHE_HIT("cache_hit"),
        CACHE_MISS("cache_miss");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public CapeRenderMetric() {
        super(new Holder(
                Counter.builder()
                        .name("cape_render_requests_total")
                        .help("Total cape render requests by cache result")
                        .labelNames("result")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("cape_render_duration_milliseconds")
                        .help("Cape render duration for cache misses (actual render time)")
                        .classicUpperBounds(5, 10, 25, 50, 100, 250, 500, 1000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordHit() {
        getValue().counter.labelValues(Result.CACHE_HIT.label()).inc();
    }

    public void recordMiss(long durationMs) {
        getValue().counter.labelValues(Result.CACHE_MISS.label()).inc();
        getValue().histogram.observe(durationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
