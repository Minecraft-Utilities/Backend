package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.player.PlayerManager;

public class DirtyPlayersBacklogMetric extends GaugeWithCallbackMetric {
    public DirtyPlayersBacklogMetric() {
        super(GaugeWithCallback.builder()
                .name("dirty_players_backlog")
                .help("Number of player cache entries pending save to MongoDB")
                .callback(callback -> {
                    if (PlayerManager.INSTANCE != null) {
                        callback.call(PlayerManager.INSTANCE.getDirtyCount());
                    }
                })
                .register(MetricService.REGISTRY));
    }
}
