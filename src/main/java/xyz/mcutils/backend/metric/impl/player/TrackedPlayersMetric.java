package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.StatisticsService;

public class TrackedPlayersMetric extends GaugeWithCallbackMetric {
    public TrackedPlayersMetric(StatisticsService statisticsService) {
        super(GaugeWithCallback.builder().name("tracked_players").callback(callback -> {
            callback.call(statisticsService.getTrackedPlayerCount());
        }).register(MetricService.REGISTRY));
    }
}