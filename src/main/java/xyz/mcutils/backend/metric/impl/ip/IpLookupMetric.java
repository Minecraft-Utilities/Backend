package xyz.mcutils.backend.metric.impl.ip;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks IP geo/ASN lookup outcomes and latency.
 * Only counts actual lookups (Spring {@code @Cacheable} misses); cached responses bypass this metric.
 */
public class IpLookupMetric extends Metric<IpLookupMetric.Holder> {

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

    public IpLookupMetric() {
        super(new Holder(
                Counter.builder()
                        .name("ip_lookup_requests_total")
                        .help("Total IP geo/ASN lookup requests by result (Spring cache misses only)")
                        .labelNames("result")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("ip_lookup_duration_milliseconds")
                        .help("IP geo/ASN lookup duration (Spring cache misses only)")
                        .classicUpperBounds(5, 10, 25, 50, 100, 250, 500, 1000, 2500)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(Result result, long durationMs) {
        getValue().counter.labelValues(result.label()).inc();
        getValue().histogram.observe(durationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
