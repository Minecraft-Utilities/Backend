package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks in-memory player cache hits, misses, and current cache size.
 */
public class PlayerCacheMetric extends Metric<PlayerCacheMetric.Holder> {

    public PlayerCacheMetric(PlayerManager playerManager) {
        super(new Holder(
                Counter.builder()
                        .name("player_cache_hits_total")
                        .help("Total in-memory player cache hits")
                        .register(MetricService.REGISTRY),
                Counter.builder()
                        .name("player_cache_misses_total")
                        .help("Total in-memory player cache misses (falls through to MongoDB)")
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("player_cache_size_entries")
                        .help("Current number of entries in the in-memory player cache")
                        .callback(callback -> callback.call(playerManager.getCacheSize()))
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
