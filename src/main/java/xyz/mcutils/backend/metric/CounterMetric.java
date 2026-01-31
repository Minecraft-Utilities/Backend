package xyz.mcutils.backend.metric;

import io.prometheus.metrics.core.metrics.Counter;

public class CounterMetric extends Metric<Counter> {
    public CounterMetric(Counter value) {
        super(value);
    }
}
