package xyz.mcutils.backend.metric.impl.mojang;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.MojangService;

/**
 * Exposes the current count of server hashes blocked by Mojang as a gauge.
 * Updated on the daily Mojang fetch schedule.
 */
public class MojangBlockedServersMetric extends Metric<MojangBlockedServersMetric.Holder> {

    public MojangBlockedServersMetric(MojangService mojangService) {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("mojang_blocked_servers_count")
                        .help("Number of server hashes currently blocked by Mojang")
                        .callback(callback -> callback.call(mojangService.getBlockedServerHashes().size()))
                        .register(MetricService.REGISTRY)
        ));
    }

    public record Holder(GaugeWithCallback gauge) {}
}
