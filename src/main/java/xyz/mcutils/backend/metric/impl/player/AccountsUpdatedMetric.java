package xyz.mcutils.backend.metric.impl.player;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Counter for tracked player accounts successfully refreshed.
 */
public class AccountsUpdatedMetric extends Metric {
    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("accounts_updated_total");

    public AccountsUpdatedMetric() {
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
