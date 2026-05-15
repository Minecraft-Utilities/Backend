package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.StatisticsService;

public class NameChangesMetric extends GaugeWithCallbackMetric {
    public NameChangesMetric(StatisticsService statisticsService) {
        super(GaugeWithCallback.builder()
                .name("name_changes_total")
                .help("Total number of tracked player name changes")
                .callback(callback -> callback.call(statisticsService.getNameChangesCount()))
                .register(MetricService.REGISTRY));
    }
}
