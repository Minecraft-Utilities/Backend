package xyz.mcutils.backend.metric.impl.jvm;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

import java.lang.management.MemoryUsage;

public class MemoryUsageMetric extends GaugeWithCallbackMetric {
    public MemoryUsageMetric() {
        super(GaugeWithCallback.builder()
                .name("process_memory_usage_bytes")
                .callback(callback -> {
                    MemoryUsage heapUsage = MetricService.MEMORY_BEAN.getHeapMemoryUsage();
                    callback.call(heapUsage.getUsed());
                })
                .register(MetricService.REGISTRY));
    }
}
