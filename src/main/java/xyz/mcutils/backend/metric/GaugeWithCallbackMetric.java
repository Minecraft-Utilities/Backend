package xyz.mcutils.backend.metric;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;

public class GaugeWithCallbackMetric extends Metric<GaugeWithCallback> {
    public GaugeWithCallbackMetric(GaugeWithCallback value) {
        super(value);
    }
}
