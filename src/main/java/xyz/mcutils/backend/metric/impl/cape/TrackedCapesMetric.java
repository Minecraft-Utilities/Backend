package xyz.mcutils.backend.metric.impl.cape;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.concurrent.TimeUnit;

public class TrackedCapesMetric extends Metric {
    private final StatisticsService statisticsService;

    public TrackedCapesMetric(StatisticsService statisticsService) {
        super(TimeUnit.SECONDS.toMillis(5L));
        this.statisticsService = statisticsService;
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("tracked_capes")
                .addField("value", this.statisticsService.getTrackedCapeCount());
    }
}
