package xyz.mcutils.backend.metric.impl.player;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Counts player submission outcomes at enqueue time.
 */
public class PlayerSubmitOutcomesMetric extends Metric {
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

    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("player_submissions_total");

    public PlayerSubmitOutcomesMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void inc(Outcome outcome, long count) {
        if (count > 0L) {
            this.counter.increment(count, List.of(outcome.label()));
        }
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        return this.counter.drain((point, tags) -> point.addTag("outcome", tags.get(0)));
    }
}
