package xyz.mcutils.backend.metric.impl.skin;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks skin render cache hits/misses and render duration for cache misses.
 */
public class SkinRenderMetric extends Metric {
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

    private final TaggedCounterBuffer counter = new TaggedCounterBuffer("skin_render_requests_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("skin_render_duration_milliseconds");

    public SkinRenderMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void recordHit() {
        this.counter.increment(List.of(Result.CACHE_HIT.label()));
    }

    public void recordMiss(long durationMs) {
        this.counter.increment(List.of(Result.CACHE_MISS.label()));
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
        points.addAll(this.counter.drain((point, tags) -> point.addTag("result", tags.get(0))));
        points.addAll(this.durations.drain((point, tags) -> { }));
        return points;
    }
}
