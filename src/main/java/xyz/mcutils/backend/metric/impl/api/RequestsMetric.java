package xyz.mcutils.backend.metric.impl.api;

import io.prometheus.metrics.core.metrics.Counter;
import xyz.mcutils.backend.metric.CounterMetric;
import xyz.mcutils.backend.service.MetricService;

public class RequestsMetric extends CounterMetric {
    public RequestsMetric() {
        super(Counter.builder()
                .name("requests")
                .register(MetricService.REGISTRY));
    }
}
