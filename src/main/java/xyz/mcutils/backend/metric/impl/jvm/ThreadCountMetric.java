package xyz.mcutils.backend.metric.impl.jvm;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

public class ThreadCountMetric extends GaugeWithCallbackMetric {
    public ThreadCountMetric() {
        super(GaugeWithCallback.builder()
                .name("process_thread_count")
                .help("Number of live threads in the JVM")
                .callback(callback -> callback.call(Constants.THREAD_BEAN.getThreadCount()))
                .register(MetricService.REGISTRY));
    }
}
