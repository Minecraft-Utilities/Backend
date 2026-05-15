package xyz.mcutils.backend.service;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.impl.api.ExternalApiRequestsMetric;
import xyz.mcutils.backend.metric.impl.api.RequestsMetric;
import xyz.mcutils.backend.metric.impl.cape.CapeRenderMetric;
import xyz.mcutils.backend.metric.impl.cape.TrackedCapesMetric;
import xyz.mcutils.backend.metric.impl.dns.DnsQueryMetric;
import xyz.mcutils.backend.metric.impl.ip.IpLookupMetric;
import xyz.mcutils.backend.metric.impl.jvm.*;
import xyz.mcutils.backend.metric.impl.mojang.MojangBlockedServersMetric;
import xyz.mcutils.backend.metric.impl.player.*;
import xyz.mcutils.backend.metric.impl.server.ServerLookupMetric;
import xyz.mcutils.backend.metric.impl.skin.SkinRenderMetric;
import xyz.mcutils.backend.metric.impl.skin.TrackedSkinsMetric;
import xyz.mcutils.backend.metric.impl.storage.StorageOperationMetric;
import xyz.mcutils.backend.metric.impl.websocket.WebSocketConnectionsMetric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MetricService {
    public static final PrometheusRegistry REGISTRY = new PrometheusRegistry();
    private static final Map<Class<?>, Metric<?>> metrics = new ConcurrentHashMap<>();

    public MetricService(@Lazy PlayerSubmitService playerSubmitService, @Lazy PlayerService playerService, @Lazy MojangService mojangService, @Lazy StatisticsService statisticsService) {
        // DNS
        this.registerMetric(new DnsQueryMetric());

        // IP
        this.registerMetric(new IpLookupMetric());

        // Storage
        this.registerMetric(new StorageOperationMetric());

        // JVM
        this.registerMetric(new MemoryUsageMetric());
        this.registerMetric(new MemoryHeapMaxMetric());
        this.registerMetric(new MemoryNonHeapMetric());
        this.registerMetric(new CpuUsageMetric());
        this.registerMetric(new ThreadCountMetric());
        this.registerMetric(new GcMetric());

        // API
        this.registerMetric(new RequestsMetric());
        this.registerMetric(new ExternalApiRequestsMetric());

        // Player
        this.registerMetric(new TrackedPlayersMetric(statisticsService));
        this.registerMetric(new AccountsUpdatedMetric());
        this.registerMetric(new NameChangesMetric(statisticsService));
        this.registerMetric(new SubmissionQueueSizeMetric(playerSubmitService));
        this.registerMetric(new PlayerSubmitOutcomesMetric());
        this.registerMetric(new PlayerSubmitProcessingMetric());
        this.registerMetric(new TopSubmittedPlayersMetric(playerService));

        // Skin
        this.registerMetric(new TrackedSkinsMetric(statisticsService));
        this.registerMetric(new SkinRenderMetric());

        // Cape
        this.registerMetric(new TrackedCapesMetric(statisticsService));
        this.registerMetric(new CapeRenderMetric());

        // Server
        this.registerMetric(new ServerLookupMetric());

        // Mojang
        this.registerMetric(new MojangBlockedServersMetric(mojangService));

        // WebSocket
        this.registerMetric(new WebSocketConnectionsMetric());
    }

    /**
     * Gets a metric by its class
     *
     * @param metricClass the metric to get
     * @param <T>         the class to cast the metric by
     * @return the metric
     */
    @SuppressWarnings("unchecked")
    public static <T extends Metric<?>> T getMetric(Class<T> metricClass) {
        return (T) metrics.get(metricClass);
    }

    /**
     * Registers a metric
     *
     * @param metric the metric to register
     */
    private void registerMetric(Metric<?> metric) {
        metrics.put(metric.getClass(), metric);
    }
}
