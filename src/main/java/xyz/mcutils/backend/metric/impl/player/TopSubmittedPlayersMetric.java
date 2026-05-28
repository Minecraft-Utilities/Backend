package xyz.mcutils.backend.metric.impl.player;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.PlayerService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Exposes the top 10 players by total submitted UUIDs.
 */
public class TopSubmittedPlayersMetric extends Metric {
    private final PlayerService playerService;

    public TopSubmittedPlayersMetric(PlayerService playerService) {
        super(TimeUnit.SECONDS.toMillis(5L));
        this.playerService = playerService;
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        List<MetricPoint> points = new ArrayList<>();
        for (PlayerRow player : this.playerService.getTopSubmittedPlayers(10)) {
            points.add(MetricPoint.measurement("top_submitted_players_submitted_uuids")
                    .addTag("username", player.getUsername())
                    .addField("value", player.getSubmittedUuids()));
        }
        return points;
    }
}
