package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerService;

/**
 * Exposes the top 10 players by total submitted UUIDs as a gauge, read directly from MongoDB.
 * Use {@code topk(10, top_submitted_players_submitted_uuids)} in Grafana.
 */
public class TopSubmittedPlayersMetric extends Metric<TopSubmittedPlayersMetric.Holder> {

    public TopSubmittedPlayersMetric(PlayerService playerService) {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("top_submitted_players_submitted_uuids")
                        .help("Submitted UUID count for the top 10 players by submission count")
                        .labelNames("username")
                        .callback(callback -> {
                            for (PlayerRow player : playerService.getTopSubmittedPlayers(10)) {
                                callback.call(player.getSubmittedUuids(), player.getUsername());
                            }
                        })
                        .register(MetricService.REGISTRY)
        ));
    }

    public record Holder(GaugeWithCallback gauge) {}
}
