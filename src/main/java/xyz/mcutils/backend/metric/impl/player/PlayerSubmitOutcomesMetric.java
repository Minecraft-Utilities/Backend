package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Counts player submission outcomes at enqueue time.
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code enqueued} – UUID was newly added to the submit queue</li>
 *   <li>{@code already_tracked} – UUID already exists in MongoDB</li>
 *   <li>{@code already_queued} – UUID already present in the Redis queue set</li>
 * </ul>
 */
public class PlayerSubmitOutcomesMetric extends Metric<PlayerSubmitOutcomesMetric.Holder> {
    public enum Outcome {
        ENQUEUED("enqueued"),
        ALREADY_TRACKED("already_tracked"),
        ALREADY_QUEUED("already_queued");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public PlayerSubmitOutcomesMetric() {
        super(new Holder(
                Counter.builder()
                        .name("player_submissions_total")
                        .help("Total player UUID submissions by outcome at enqueue time")
                        .labelNames("outcome")
                        .register(MetricService.REGISTRY)
        ));
    }

    public void inc(Outcome outcome, long count) {
        if (count > 0) {
            getValue().counter.labelValues(outcome.label()).inc(count);
        }
    }

    public record Holder(Counter counter) {}
}
