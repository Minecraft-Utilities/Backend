package xyz.mcutils.backend.metric.util;

import xyz.mcutils.backend.metric.MetricPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Thread-safe duration buffer that drains aggregated timing stats into metric points.
 */
public final class TaggedDurationBuffer {
    private final String measurement;
    private final ConcurrentHashMap<List<String>, DurationStats> stats = new ConcurrentHashMap<>();

    public TaggedDurationBuffer(String measurement) {
        this.measurement = measurement;
    }

    public void record(List<String> tagValues, long durationMs) {
        this.stats.computeIfAbsent(tagValues, ignored -> new DurationStats()).record(durationMs);
    }

    public List<MetricPoint> drain(BiConsumer<MetricPoint, List<String>> tagger) {
        List<MetricPoint> points = new ArrayList<>();
        for (Map.Entry<List<String>, DurationStats> entry : this.stats.entrySet()) {
            DurationSnapshot snapshot = entry.getValue().drain();
            if (snapshot.count() <= 0L) {
                continue;
            }
            MetricPoint point = MetricPoint.measurement(this.measurement)
                    .addField("count", snapshot.count())
                    .addField("sum", snapshot.sum())
                    .addField("max", snapshot.max());
            tagger.accept(point, entry.getKey());
            points.add(point);
        }
        return points;
    }

    private static final class DurationStats {
        private long count;
        private double sum;
        private long max;

        synchronized void record(long durationMs) {
            this.count++;
            this.sum += durationMs;
            if (durationMs > this.max) {
                this.max = durationMs;
            }
        }

        synchronized DurationSnapshot drain() {
            if (this.count <= 0L) {
                return DurationSnapshot.EMPTY;
            }
            DurationSnapshot snapshot = new DurationSnapshot(this.count, this.sum, this.max);
            this.count = 0L;
            this.sum = 0.0;
            this.max = 0L;
            return snapshot;
        }
    }

    private record DurationSnapshot(long count, double sum, long max) {
        private static final DurationSnapshot EMPTY = new DurationSnapshot(0L, 0.0, 0L);
    }
}
