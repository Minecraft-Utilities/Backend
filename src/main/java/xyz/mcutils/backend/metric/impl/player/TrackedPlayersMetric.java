package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerService;

public class TrackedPlayersMetric extends GaugeWithCallbackMetric {
    public TrackedPlayersMetric() {
        super(GaugeWithCallback.builder()
                .name("tracked_players")
                .callback(callback -> {
                    callback.call(PlayerService.INSTANCE.getTrackedPlayerCount());
                })
                .register(MetricService.REGISTRY));
    }
}