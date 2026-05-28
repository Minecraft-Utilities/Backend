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
 * Generic metric for external API requests (e.g. Mojang player lookup, username lookup).
 */
public class ExternalApiRequestsMetric extends Metric {
    private final TaggedCounterBuffer counters = new TaggedCounterBuffer("external_api_requests_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("external_api_request_duration_milliseconds");

    public ExternalApiRequestsMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(String api, String endpoint, boolean success, long durationMs) {
        String status = success ? "success" : "failure";
        this.counters.increment(List.of(api, endpoint, status));
        this.durations.record(List.of(api, endpoint), durationMs);
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
                .addTag("api", tags.get(0))
                .addTag("endpoint", tags.get(1))
                .addTag("status", tags.get(2))));
        points.addAll(this.durations.drain((point, tags) -> point
                .addTag("api", tags.get(0))
                .addTag("endpoint", tags.get(1))));
        return points;
    }
}
