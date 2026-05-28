package xyz.mcutils.backend.metric.impl.mojang;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.service.MojangService;

import java.util.concurrent.TimeUnit;

/**
 * Exposes the current count of server hashes blocked by Mojang.
 */
public class MojangBlockedServersMetric extends Metric {
    private final MojangService mojangService;

    public MojangBlockedServersMetric(MojangService mojangService) {
        super(TimeUnit.SECONDS.toMillis(60L));
        this.mojangService = mojangService;
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("mojang_blocked_servers_count")
                .addField("value", this.mojangService.getBlockedServerHashes().size());
    }
}
