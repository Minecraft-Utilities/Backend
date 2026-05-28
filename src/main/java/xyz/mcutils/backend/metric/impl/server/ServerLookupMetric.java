package xyz.mcutils.backend.metric.impl.server;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server lookup cache hits/misses and server ping duration for cache misses.
 */
public class ServerLookupMetric extends Metric {
    public enum Result {
        CACHE_HIT("cache_hit"),
        CACHE_MISS("cache_miss");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("server_lookups_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("server_ping_duration_milliseconds");

    public ServerLookupMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void recordHit() {
        this.counter.increment(List.of(Result.CACHE_HIT.label()));
    }

    public void recordMiss(long pingDurationMs) {
        this.counter.increment(List.of(Result.CACHE_MISS.label()));
        this.durations.record(List.of(), pingDurationMs);
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        List<MetricPoint> points = new ArrayList<>();
        points.addAll(this.counter.drain((point, tags) -> point.addTag("result", tags.get(0))));
        points.addAll(this.durations.drain((point, tags) -> { }));
        return points;
    }
}
