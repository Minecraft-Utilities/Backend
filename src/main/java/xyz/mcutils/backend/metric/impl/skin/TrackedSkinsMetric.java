package xyz.mcutils.backend.metric.impl.skin;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.concurrent.TimeUnit;

public class TrackedSkinsMetric extends Metric {
    private final StatisticsService statisticsService;

    public TrackedSkinsMetric(StatisticsService statisticsService) {
        super(TimeUnit.SECONDS.toMillis(5L));
        this.statisticsService = statisticsService;
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("tracked_skins")
                .addField("value", this.statisticsService.getTrackedSkinCount());
    }
}
