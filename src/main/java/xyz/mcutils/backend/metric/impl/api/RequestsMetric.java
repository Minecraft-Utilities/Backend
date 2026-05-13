package xyz.mcutils.backend.metric.impl.api;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Tracks inbound API requests by endpoint, HTTP method, and status code,
 * plus a latency histogram per endpoint and method.
 */
public class RequestsMetric extends Metric<RequestsMetric.Holder> {
    public RequestsMetric() {
        super(new Holder(
                Counter.builder()
                        .name("requests_total")
                        .help("Total inbound API requests by endpoint, method, and status")
                        .labelNames("endpoint", "method", "status")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("request_duration_milliseconds")
                        .help("Inbound request duration in milliseconds")
                        .labelNames("endpoint", "method")
                        .classicUpperBounds(5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000)
                        .register(MetricService.REGISTRY)
        ));
    }

    public void record(String endpoint, String method, int status, long durationMs) {
        Holder h = getValue();
        h.counter.labelValues(endpoint, method, String.valueOf(status)).inc();
        h.histogram.labelValues(endpoint, method).observe(durationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {}
}
