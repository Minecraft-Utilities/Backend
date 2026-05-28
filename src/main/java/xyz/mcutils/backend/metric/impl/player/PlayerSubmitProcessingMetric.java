package xyz.mcutils.backend.metric.impl.player;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks processing outcomes and per-player duration in the submit queue consumer.
 */
public class PlayerSubmitProcessingMetric extends Metric {
    public enum Outcome {
        CREATED("created"),
        NOT_FOUND("not_found"),
        RATE_LIMITED("rate_limited"),
        TIMED_OUT("timed_out");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("player_submission_processed_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("player_submission_processing_duration_milliseconds");

    public PlayerSubmitProcessingMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(Outcome outcome, long durationMs) {
        this.counter.increment(List.of(outcome.label()));
        this.durations.record(List.of(), durationMs);
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        List<MetricPoint> points = new ArrayList<>();
        points.addAll(this.counter.drain((point, tags) -> point.addTag("outcome", tags.get(0))));
        points.addAll(this.durations.drain((point, tags) -> { }));
        return points;
    }
}
