package xyz.mcutils.backend.metric.impl.storage;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks S3/MinIO storage operation outcomes and latency.
 */
public class StorageOperationMetric extends Metric {
    public enum Operation {
        UPLOAD("upload"),
        GET("get");

        private final String label;

        Operation(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        SUCCESS("success"),
        FAILURE("failure"),
        CACHE_HIT("cache_hit");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TaggedCounterBuffer counters = new TaggedCounterBuffer("storage_operations_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("storage_operation_duration_milliseconds");

    public StorageOperationMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(Operation operation, Status status, long durationMs) {
        this.counters.increment(List.of(operation.label(), status.label()));
        if (status != Status.CACHE_HIT) {
            this.durations.record(List.of(operation.label()), durationMs);
        }
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        List<MetricPoint> points = new ArrayList<>();
        points.addAll(this.counters.drain((point, tags) -> point
                .addTag("operation", tags.get(0))
                .addTag("status", tags.get(1))));
        points.addAll(this.durations.drain((point, tags) -> point.addTag("operation", tags.get(0))));
        return points;
    }
}
