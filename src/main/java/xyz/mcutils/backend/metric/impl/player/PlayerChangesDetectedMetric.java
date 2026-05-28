package xyz.mcutils.backend.metric.impl.player;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Counter for skin, cape, and username changes detected during player updates.
 */
public class PlayerChangesDetectedMetric extends Metric {
    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("player_changes_detected_total");

    public PlayerChangesDetectedMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void inc(long n) {
        if (n > 0L) {
            this.counter.increment(n, List.of());
        }
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        return this.counter.drain((point, tags) -> { });
    }
}
