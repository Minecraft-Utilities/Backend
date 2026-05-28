package xyz.mcutils.backend.metric.impl.api;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks inbound API requests by endpoint, HTTP method, and status code,
 * plus request duration per endpoint and method.
 */
public class RequestsMetric extends Metric {
    private final TaggedCounterBuffer counters = new TaggedCounterBuffer("requests_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("request_duration_milliseconds");

    public RequestsMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(String endpoint, String method, int status, long durationMs) {
        this.counters.increment(List.of(endpoint, method, String.valueOf(status)));
        this.durations.record(List.of(endpoint, method), durationMs);
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
                .addTag("endpoint", tags.get(0))
                .addTag("method", tags.get(1))
                .addTag("status", tags.get(2))));
        points.addAll(this.durations.drain((point, tags) -> point
                .addTag("endpoint", tags.get(0))
                .addTag("method", tags.get(1))));
        return points;
    }
}
