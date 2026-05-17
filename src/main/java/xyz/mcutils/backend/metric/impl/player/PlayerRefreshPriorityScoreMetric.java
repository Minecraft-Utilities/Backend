package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Distribution of priority scores for players selected in each background refresh chunk.
 * Diagnostic for weight tuning: high-bucket dominance suggests popularity outweighs velocity.
 */
public class PlayerRefreshPriorityScoreMetric extends Metric<PlayerRefreshPriorityScoreMetric.Holder> {

    public PlayerRefreshPriorityScoreMetric() {
        super(new Holder(Histogram.builder()
                .name("player_refresh_priority_score")
                .help("Priority score of players selected for background refresh")
                .classicUpperBounds(1, 2, 5, 10)
                .register(MetricService.REGISTRY)));
    }

    public void observe(double score) {
        getValue().histogram.observe(score);
    }

    public record Holder(Histogram histogram) {}
}
