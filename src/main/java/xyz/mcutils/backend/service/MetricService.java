package xyz.mcutils.backend.service;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.impl.api.RequestsMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryHeapMaxMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryNonHeapMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryUsageMetric;
import xyz.mcutils.backend.metric.impl.player.TrackedPlayersMetric;
import xyz.mcutils.backend.metric.impl.skin.TrackedSkinsMetric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MetricService {
    public static final PrometheusRegistry REGISTRY = new PrometheusRegistry();
    private static final Map<Class<?>, Metric<?>> metrics = new ConcurrentHashMap<>();

    public MetricService() {
        // JVM
        this.registerMetric(new MemoryUsageMetric());
        this.registerMetric(new MemoryHeapMaxMetric());
        this.registerMetric(new MemoryNonHeapMetric());

        // API
        this.registerMetric(new RequestsMetric());

        // Player
        this.registerMetric(new TrackedPlayersMetric());

        // Skin
        this.registerMetric(new TrackedSkinsMetric());
    }

    /**
     * Registers a metric
     *
     * @param metric the metric to register
     */
    private void registerMetric(Metric<?> metric) {
        metrics.put(metric.getClass(), metric);
    }

    /**
     * Gets a metric by its class
     *
     * @param metricClass the metric to get
     * @return the metric
     * @param <T> the class to cast the metric by
     */
    public static <T extends Metric<?>> T getMetric(Class<T> metricClass) {
        return (T) metrics.get(metricClass);
    }
}
