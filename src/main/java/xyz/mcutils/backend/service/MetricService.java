package xyz.mcutils.backend.service;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.impl.api.ExternalApiRequestsMetric;
import xyz.mcutils.backend.metric.impl.api.RequestsMetric;
import xyz.mcutils.backend.metric.impl.cape.skin.DirtyCapesBacklogMetric;
import xyz.mcutils.backend.metric.impl.cape.skin.TrackedCapesMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryHeapMaxMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryNonHeapMetric;
import xyz.mcutils.backend.metric.impl.jvm.MemoryUsageMetric;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.metric.impl.player.DirtyPlayersBacklogMetric;
import xyz.mcutils.backend.metric.impl.player.SubmissionQueueSizeMetric;
import xyz.mcutils.backend.metric.impl.player.TrackedPlayersMetric;
import xyz.mcutils.backend.metric.impl.skin.DirtySkinsBacklogMetric;
import xyz.mcutils.backend.metric.impl.skin.TrackedSkinsMetric;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.skin.SkinManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MetricService {
    public static final PrometheusRegistry REGISTRY = new PrometheusRegistry();
    private static final Map<Class<?>, Metric<?>> metrics = new ConcurrentHashMap<>();

    public MetricService(
            @Lazy PlayerSubmitService playerSubmitService,
            @Lazy PlayerService playerService,
            @Lazy PlayerManager playerManager,
            @Lazy SkinService skinService,
            @Lazy SkinManager skinManager,
            @Lazy CapeService capeService,
            @Lazy CapeManager capeManager) {
        // JVM
        this.registerMetric(new MemoryUsageMetric());
        this.registerMetric(new MemoryHeapMaxMetric());
        this.registerMetric(new MemoryNonHeapMetric());

        // API
        this.registerMetric(new RequestsMetric());
        this.registerMetric(new ExternalApiRequestsMetric());

        // Player
        this.registerMetric(new TrackedPlayersMetric(playerService));
        this.registerMetric(new AccountsUpdatedMetric());
        this.registerMetric(new SubmissionQueueSizeMetric(playerSubmitService));
        this.registerMetric(new DirtyPlayersBacklogMetric(playerManager));

        // Skin
        this.registerMetric(new TrackedSkinsMetric(skinService));
        this.registerMetric(new DirtySkinsBacklogMetric(skinManager));

        // Cape
        this.registerMetric(new TrackedCapesMetric(capeService));
        this.registerMetric(new DirtyCapesBacklogMetric(capeManager));
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
