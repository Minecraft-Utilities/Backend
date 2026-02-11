package xyz.mcutils.backend.metric.impl.cape.skin;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.MetricService;

public class TrackedCapesMetric extends GaugeWithCallbackMetric {
    public TrackedCapesMetric() {
        super(GaugeWithCallback.builder()
                .name("tracked_capes")
                .callback(callback -> {
                    callback.call(CapeService.INSTANCE.getTrackedCapeCount());
                })
                .register(MetricService.REGISTRY));
    }
}