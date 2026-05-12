package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.service.MetricService;

public class DirtyPlayersBacklogMetric extends GaugeWithCallbackMetric {
    public DirtyPlayersBacklogMetric(PlayerManager playerManager) {
        super(GaugeWithCallback.builder().name("dirty_players_backlog").help("Number of player cache entries pending save to MongoDB").callback(callback -> {
            callback.call(playerManager.getDirtyCount());
        }).register(MetricService.REGISTRY));
    }
}
