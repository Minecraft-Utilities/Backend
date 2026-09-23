package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exposes the top 10 players by total submitted UUIDs as a gauge.
 * The callback reads an in-memory snapshot refreshed on a schedule, so scraping /metrics
 * never executes a Postgres query.
 * <p>
 * The snapshot holder is static because the registered gauge callback is built inside the
 * {@code super(...)} call, which cannot reference the not-yet-constructed instance; this class
 * is a singleton registered once in {@link MetricService}.
 */
public class TopSubmittedPlayersMetric extends Metric<TopSubmittedPlayersMetric.Holder> {

    private static final AtomicReference<List<PlayerRow>> SNAPSHOT = new AtomicReference<>(List.of());

    private final PlayerService playerService;

    public TopSubmittedPlayersMetric(PlayerService playerService) {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("top_submitted_players_submitted_uuids")
                        .help("Submitted UUID count for the top 10 players by submission count")
                        .labelNames("username")
                        .callback(callback -> {
                            for (PlayerRow player : SNAPSHOT.get()) {
                                callback.call(player.getSubmittedUuids(), player.getUsername());
                            }
                        })
                        .register(MetricService.REGISTRY)
        ));
        this.playerService = playerService;
    }

    /**
     * Refreshes the in-memory snapshot. Called by a scheduler, never from the scrape callback.
     */
    public void refresh() {
        SNAPSHOT.set(this.playerService.getTopSubmittedPlayers(10));
    }

    public record Holder(GaugeWithCallback gauge) {}
}
