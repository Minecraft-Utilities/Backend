package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.InfluxDB;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j(topic = "Metrics")
public class MetricService {
    private static final List<Metric> METRICS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<Class<?>, Metric> METRIC_MAP = new ConcurrentHashMap<>();

    private final InfluxDB influx;
    private final Environment environment;
    private final String serverName;

    public MetricService(
            InfluxDB influx,
            Environment environment,
            @Lazy PlayerSubmitService playerSubmitService,
            @Lazy PlayerService playerService,
            @Lazy MojangService mojangService,
            @Lazy StatisticsService statisticsService,
            @Value("${mc-utils.influxdb.server:backend}")
            String serverName
    ) {
        this.influx = influx;
        this.environment = environment;
        this.serverName = serverName;

        this.registerMetric(new DnsQueryMetric());
        this.registerMetric(new IpLookupMetric());
        this.registerMetric(new StorageOperationMetric());
        this.registerMetric(new MemoryUsageMetric());
        this.registerMetric(new MemoryHeapMaxMetric());
        this.registerMetric(new MemoryNonHeapMetric());
        this.registerMetric(new CpuUsageMetric());
        this.registerMetric(new ThreadCountMetric());
        this.registerMetric(new GcMetric());
        this.registerMetric(new RequestsMetric());
        this.registerMetric(new ExternalApiRequestsMetric());
        this.registerMetric(new TrackedPlayersMetric(statisticsService));
        this.registerMetric(new AccountsUpdatedMetric());
        this.registerMetric(new PlayerChangesDetectedMetric());
        this.registerMetric(new NameChangesMetric(statisticsService));
        this.registerMetric(new SubmissionQueueSizeMetric(playerSubmitService));
        this.registerMetric(new PlayerSubmitOutcomesMetric());
        this.registerMetric(new PlayerSubmitProcessingMetric());
        this.registerMetric(new TopSubmittedPlayersMetric(playerService));
        this.registerMetric(new TrackedSkinsMetric(statisticsService));
        this.registerMetric(new SkinRenderMetric());
        this.registerMetric(new TrackedCapesMetric(statisticsService));
        this.registerMetric(new CapeRenderMetric());
        this.registerMetric(new ServerLookupMetric());
        this.registerMetric(new MojangBlockedServersMetric(mojangService));
        this.registerMetric(new WebSocketConnectionsMetric());

        log.info("Registered {} metrics", METRICS.size());
    }

    @Scheduled(fixedRate = 1000L)
    public void track() {
        if (!this.influx.isConnected()) {
            return;
        }

        List<MetricPoint> points = new ArrayList<>();
        long currentMillis = System.currentTimeMillis();

        for (Metric metric : METRICS) {
            try {
                points.addAll(metric.trackAll(currentMillis));
            } catch (Exception ex) {
                log.error("An error occurred while tracking metrics", ex);
            }
        }

        if (points.isEmpty()) {
            return;
        }

        points.add(MetricPoint.measurement("pointsPerSecond")
                .addField("value", points.size() + 1)
                .time(currentMillis));

        String environmentTag = Arrays.stream(this.environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("production") || profile.equalsIgnoreCase("prod"))
                ? "production"
                : "development";
        for (MetricPoint point : points) {
            point.addTag("environment", environmentTag);
            point.addTag("server", this.serverName);
        }

        this.influx.writePoints(points);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Metric> T getMetric(Class<T> metricClass) {
        return (T) METRIC_MAP.get(metricClass);
    }

    public static <T extends Metric> Optional<T> findMetric(Class<T> clazz) {
        return Optional.ofNullable(getMetric(clazz));
    }

    private void registerMetric(Metric metric) {
        METRICS.add(metric);
        METRIC_MAP.put(metric.getClass(), metric);
        log.info("Registered metric: {}", metric.getName());
    }
}
