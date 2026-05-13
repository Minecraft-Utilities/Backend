package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks processing outcomes and per-player duration in the submit queue consumer.
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code created} – player profile fetched from Mojang and created in the database</li>
 *   <li>{@code not_found} – player UUID not found on Mojang</li>
 *   <li>{@code rate_limited} – request was rate-limited; entry re-queued</li>
 * </ul>
 */
public class PlayerSubmitProcessingMetric extends Metric<PlayerSubmitProcessingMetric.Holder> {
    public enum Outcome {
        CREATED("created"),
        NOT_FOUND("not_found"),
        RATE_LIMITED("rate_limited");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public PlayerSubmitProcessingMetric() {
        super(new Holder(
                Counter.builder()
                        .name("player_submission_processed_total")
                        .help("Total player submissions processed from the queue, by outcome")
                        .labelNames("outcome")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("player_submission_processing_duration_milliseconds")
                        .help("Per-player processing duration in the submit queue consumer")
                        .classicUpperBounds(50, 100, 250, 500, 1000, 2500, 5000, 10000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(Outcome outcome, long durationMs) {
        getValue().counter.labelValues(outcome.label()).inc();
        getValue().histogram.observe(durationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
