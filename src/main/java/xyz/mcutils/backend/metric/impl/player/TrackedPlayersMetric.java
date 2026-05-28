package xyz.mcutils.backend.metric.impl.player;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.service.StatisticsService;

import java.util.concurrent.TimeUnit;

public class TrackedPlayersMetric extends Metric {
    private final StatisticsService statisticsService;

    public TrackedPlayersMetric(StatisticsService statisticsService) {
        super(TimeUnit.SECONDS.toMillis(5L));
        this.statisticsService = statisticsService;
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("tracked_players")
                .addField("value", this.statisticsService.getTrackedPlayerCount());
    }
}
