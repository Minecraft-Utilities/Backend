package xyz.mcutils.backend.metric.impl.dns;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks DNS SRV and A query outcomes and latency.
 * Cache hits are counted but excluded from the duration histogram.
 */
public class DnsQueryMetric extends Metric<DnsQueryMetric.Holder> {

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

    public DnsQueryMetric() {
        super(new Holder(
                Counter.builder()
                        .name("dns_queries_total")
                        .help("Total DNS queries by query type and result")
                        .labelNames("type", "result")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("dns_query_duration_milliseconds")
                        .help("DNS query duration (excludes in-memory cache hits)")
                        .labelNames("type")
                        .classicUpperBounds(1, 5, 10, 25, 50, 100, 250, 500, 1000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(QueryType type, Result result, long durationMs) {
        getValue().counter.labelValues(type.label(), result.label()).inc();
        if (result != Result.CACHE_HIT) {
            getValue().histogram.labelValues(type.label()).observe(durationMs);
        }
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
