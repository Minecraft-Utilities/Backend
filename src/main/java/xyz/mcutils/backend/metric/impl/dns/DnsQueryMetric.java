package xyz.mcutils.backend.metric.impl.dns;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.metric.util.TaggedCounterBuffer;
import xyz.mcutils.backend.metric.util.TaggedDurationBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tracks DNS SRV and A query outcomes and latency.
 * Cache hits are counted but excluded from the duration histogram.
 */
public class DnsQueryMetric extends Metric {
    public enum QueryType {
        SRV("srv"),
        A("a");

        private final String label;

        QueryType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Result {
        CACHE_HIT("cache_hit"),
        RESOLVED("resolved"),
        NOT_FOUND("not_found");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TaggedCounterBuffer counters = new TaggedCounterBuffer("dns_queries_total");
    private final TaggedDurationBuffer durations = new TaggedDurationBuffer("dns_query_duration_milliseconds");

    public DnsQueryMetric() {
        super(TimeUnit.SECONDS.toMillis(1L));
    }

    public void record(QueryType type, Result result, long durationMs) {
        this.counters.increment(List.of(type.label(), result.label()));
        if (result != Result.CACHE_HIT) {
            this.durations.record(List.of(type.label()), durationMs);
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
                .addTag("type", tags.get(0))
                .addTag("result", tags.get(1))));
        points.addAll(this.durations.drain((point, tags) -> point.addTag("type", tags.get(0))));
        return points;
    }
}
