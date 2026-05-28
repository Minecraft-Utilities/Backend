package xyz.mcutils.backend.metric.impl.jvm;

import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

public class CpuUsageMetric extends Metric {
    public CpuUsageMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    public MetricPoint buildPoint() {
        OperatingSystemMXBean raw = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = -1;
        if (raw instanceof com.sun.management.OperatingSystemMXBean osBean) {
            cpuLoad = osBean.getProcessCpuLoad();
        }
        return MetricPoint.measurement("process_cpu_usage")
                .addField("value", cpuLoad < 0 ? 0.0 : cpuLoad);
    }
}
