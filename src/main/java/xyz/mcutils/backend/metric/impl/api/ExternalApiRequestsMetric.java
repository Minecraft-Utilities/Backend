package xyz.mcutils.backend.metric.impl.api;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Generic metric for external API requests (e.g. Mojang player lookup, username lookup).
 * Records request count by api, endpoint, and status (success/failure), and request duration.
 */
public class ExternalApiRequestsMetric extends Metric<ExternalApiRequestsMetric.Holder> {

    public ExternalApiRequestsMetric() {
        super(new Holder(
                Counter.builder()
                        .name("external_api_requests_total")
                        .help("Total external API requests by api, endpoint and status")
                        .labelNames("api", "endpoint", "status")
                        .register(MetricService.REGISTRY),
                Histogram.builder()
                        .name("external_api_request_duration_milliseconds")
                        .help("External API request duration in milliseconds")
                        .labelNames("api", "endpoint")
                        .classicUpperBounds(10, 25, 50, 100, 250, 500, 1000, 2500, 5000)
                        .register(MetricService.REGISTRY)
        ));
    }

    /**
     * Records one external API request.
     *
     * @param api        API name (e.g. "mojang")
     * @param endpoint   Endpoint/operation name (e.g. "player_lookup", "username_lookup")
     * @param success    whether the request succeeded
     * @param durationMs duration of the request in milliseconds
     */
    public void record(String api, String endpoint, boolean success, long durationMs) {
        Holder h = getValue();
        String status = success ? "success" : "failure";
        h.counter.labelValues(api, endpoint, status).inc();
        h.histogram.labelValues(api, endpoint).observe(durationMs);
    }

    public record Holder(Counter counter, Histogram histogram) {
    }
}
