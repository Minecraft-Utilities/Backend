package xyz.mcutils.backend.metric.impl.server;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks server lookup cache hits/misses and server ping duration for cache misses.
 */
public class ServerLookupMetric extends Metric<ServerLookupMetric.Holder> {
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

    public ServerLookupMetric() {
        super(new Holder(
                Counter.builder()
                        .name("server_lookups_total")
                        .help("Total server lookups by cache result")
                        .labelNames("result")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("server_ping_duration_milliseconds")
                        .help("Server ping duration for cache misses")
                        .classicUpperBounds(50, 100, 250, 500, 1000, 2500, 5000, 10000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordHit() {
        getValue().counter.labelValues(Result.CACHE_HIT.label()).inc();
    }

    public void recordMiss(long pingDurationMs) {
        getValue().counter.labelValues(Result.CACHE_MISS.label()).inc();
        getValue().histogram.observe(pingDurationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
