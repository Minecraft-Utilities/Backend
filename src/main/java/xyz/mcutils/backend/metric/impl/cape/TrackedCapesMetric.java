package xyz.mcutils.backend.metric.impl.cape;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.StatisticsService;

public class TrackedCapesMetric extends GaugeWithCallbackMetric {
    public TrackedCapesMetric(StatisticsService statisticsService) {
        super(GaugeWithCallback.builder().name("tracked_capes").callback(callback -> {
            callback.call(statisticsService.getTrackedCapeCount());
        }).register(MetricService.REGISTRY));
    }
}