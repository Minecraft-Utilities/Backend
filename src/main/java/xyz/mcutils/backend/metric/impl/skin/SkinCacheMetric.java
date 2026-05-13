package xyz.mcutils.backend.metric.impl.skin;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.skin.SkinManager;

/**
 * Tracks in-memory skin cache hits, misses, and current cache size.
 */
public class SkinCacheMetric extends Metric<SkinCacheMetric.Holder> {

    public SkinCacheMetric(SkinManager skinManager) {
        super(new Holder(
                Counter.builder()
                        .name("skin_cache_hits_total")
                        .help("Total in-memory skin cache hits")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("skin_cache_misses_total")
                        .help("Total in-memory skin cache misses (falls through to MongoDB)")
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("skin_cache_size_entries")
                        .help("Current number of entries in the in-memory skin cache")
                        .callback(callback -> callback.call(skinManager.getCacheSize()))
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
