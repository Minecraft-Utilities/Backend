package xyz.mcutils.backend.metric.impl.jvm;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

import java.lang.management.MemoryUsage;

public class MemoryNonHeapMetric extends GaugeWithCallbackMetric {
    public MemoryNonHeapMetric() {
        super(GaugeWithCallback.builder()
                .name("process_memory_non_heap_bytes")
                .callback(callback -> {
                    MemoryUsage heapUsage = MetricService.MEMORY_BEAN.getNonHeapMemoryUsage();
                    callback.call(heapUsage.getUsed());
                })
                .register(MetricService.REGISTRY));
    }
}
