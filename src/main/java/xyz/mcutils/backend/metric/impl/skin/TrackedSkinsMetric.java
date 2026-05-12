package xyz.mcutils.backend.metric.impl.skin;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.SkinService;

public class TrackedSkinsMetric extends GaugeWithCallbackMetric {
    public TrackedSkinsMetric(SkinService skinService) {
        super(GaugeWithCallback.builder().name("tracked_skins").callback(callback -> {
            callback.call(skinService.getTrackedSkinCount());
        }).register(MetricService.REGISTRY));
    }
}