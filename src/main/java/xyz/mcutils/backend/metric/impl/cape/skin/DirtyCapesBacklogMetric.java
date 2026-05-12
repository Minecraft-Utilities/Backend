package xyz.mcutils.backend.metric.impl.cape.skin;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

public class DirtyCapesBacklogMetric extends GaugeWithCallbackMetric {
    public DirtyCapesBacklogMetric(CapeManager capeManager) {
        super(GaugeWithCallback.builder().name("dirty_capes_backlog").help("Number of cape cache entries pending save to MongoDB").callback(callback -> {
            callback.call(capeManager.getDirtyCount());
        }).register(MetricService.REGISTRY));
    }
}
