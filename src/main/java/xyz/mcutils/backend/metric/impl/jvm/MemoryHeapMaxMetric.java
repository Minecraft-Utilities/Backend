package xyz.mcutils.backend.metric.impl.jvm;

import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;

import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

public class MemoryHeapMaxMetric extends Metric {
    public MemoryHeapMaxMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    public MetricPoint buildPoint() {
        MemoryUsage heapUsage = Constants.MEMORY_BEAN.getHeapMemoryUsage();
        return MetricPoint.measurement("process_memory_heap_max_bytes")
                .addField("value", heapUsage.getMax());
    }
}
