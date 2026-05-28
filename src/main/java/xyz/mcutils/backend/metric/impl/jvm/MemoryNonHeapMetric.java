package xyz.mcutils.backend.metric.impl.jvm;

import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;

import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

public class MemoryNonHeapMetric extends Metric {
    public MemoryNonHeapMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    public MetricPoint buildPoint() {
        MemoryUsage nonHeap = Constants.MEMORY_BEAN.getNonHeapMemoryUsage();
        return MetricPoint.measurement("process_memory_non_heap_bytes")
                .addField("value", nonHeap.getUsed());
    }
}
