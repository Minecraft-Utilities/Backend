package xyz.mcutils.backend.metric.impl.storage;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks S3/MinIO storage operation outcomes and latency.
 * In-memory cache hits are counted but excluded from the duration histogram.
 */
public class StorageOperationMetric extends Metric<StorageOperationMetric.Holder> {

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

    public StorageOperationMetric() {
        super(new Holder(
                Counter.builder()
                        .name("storage_operations_total")
                        .help("Total S3 storage operations by operation type and status")
                        .labelNames("operation", "status")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("storage_operation_duration_milliseconds")
                        .help("S3 storage operation duration (excludes in-memory cache hits)")
                        .labelNames("operation")
                        .classicUpperBounds(5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(Operation operation, Status status, long durationMs) {
        getValue().counter.labelValues(operation.label(), status.label()).inc();
        if (status != Status.CACHE_HIT) {
            getValue().histogram.labelValues(operation.label()).observe(durationMs);
        }
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
