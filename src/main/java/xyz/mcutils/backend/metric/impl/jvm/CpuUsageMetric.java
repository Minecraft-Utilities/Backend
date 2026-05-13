package xyz.mcutils.backend.metric.impl.jvm;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class CpuUsageMetric extends GaugeWithCallbackMetric {
    public CpuUsageMetric() {
        super(GaugeWithCallback.builder()
                .name("process_cpu_usage")
                .help("Process CPU usage as a fraction (0.0 to 1.0)")
                .callback(callback -> {
                    OperatingSystemMXBean raw = ManagementFactory.getOperatingSystemMXBean();
                    double cpuLoad = -1;
                    if (raw instanceof com.sun.management.OperatingSystemMXBean osBean) {
                        cpuLoad = osBean.getProcessCpuLoad();
                    }
                    callback.call(cpuLoad < 0 ? 0 : cpuLoad);
                })
                .register(MetricService.REGISTRY));
    }
}
