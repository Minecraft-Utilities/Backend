package xyz.mcutils.backend.metric.impl.jvm;

import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;

import java.util.concurrent.TimeUnit;

public class ThreadCountMetric extends Metric {
    public ThreadCountMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("process_thread_count")
                .addField("value", Constants.THREAD_BEAN.getThreadCount());
    }
}
