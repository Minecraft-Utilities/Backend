package xyz.mcutils.backend.metric.impl.ip;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks IP geo/ASN lookup outcomes and latency.
 */
public class IpLookupMetric extends Metric {
    public enum Result {
        SUCCESS("success"),
        NOT_FOUND("not_found"),
        ERROR("error");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TaggedCounterBuffer counters = new TaggedCounterBuffer("ip_lookup_requests_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("ip_lookup_duration_milliseconds");

    public IpLookupMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(Result result, long durationMs) {
        this.counters.increment(List.of(result.label()));
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
        points.addAll(this.counters.drain((point, tags) -> point.addTag("result", tags.get(0))));
        points.addAll(this.durations.drain((point, tags) -> { }));
        return points;
    }
}
