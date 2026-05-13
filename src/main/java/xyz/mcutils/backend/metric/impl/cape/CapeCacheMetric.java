package xyz.mcutils.backend.metric.impl.cape;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks in-memory cape cache hits, misses, and current cache size.
 */
public class CapeCacheMetric extends Metric<CapeCacheMetric.Holder> {

    public CapeCacheMetric(CapeManager capeManager) {
        super(new Holder(
                Counter.builder()
                        .name("cape_cache_hits_total")
                        .help("Total in-memory cape cache hits")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("cape_cache_misses_total")
                        .help("Total in-memory cape cache misses (falls through to MongoDB)")
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("cape_cache_size_entries")
                        .help("Current number of entries in the in-memory cape cache")
                        .callback(callback -> callback.call(capeManager.getCacheSize()))
                        .register(MetricService.REGISTRY)
        ));
    }

    public void recordHit() {
        getValue().hits.inc();
    }

    public void recordMiss() {
        getValue().misses.inc();
    }

    public record Holder(Counter hits, Counter misses, GaugeWithCallback size) {}
}
