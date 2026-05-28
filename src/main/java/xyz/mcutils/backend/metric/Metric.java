package xyz.mcutils.backend.metric;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Represents a tracker for metrics.
 */
@Getter
public abstract class Metric {
    private final String name;
    private final boolean async;
    private final long interval;
    private long lastExecution;

    protected Metric(long interval) {
        this(interval, true);
    }

    protected Metric(long interval, boolean async) {
        this.name = this.getClass().getSimpleName();
        this.async = async;
        this.interval = interval;
    }

    /**
     * Build the point for this metric.
     *
     * @return the built point, null to not track
     */
    @Nullable
    public abstract MetricPoint buildPoint();

    /**
     * Build all points for this metric.
     * Defaults to a single {@link #buildPoint()}; override for multi-point metrics.
     */
    public List<MetricPoint> buildPoints() {
        MetricPoint point = this.buildPoint();
        return point == null ? Collections.emptyList() : List.of(point);
    }

    public final List<MetricPoint> trackAll(long now) {
        if ((now - this.lastExecution) < this.interval) {
            return Collections.emptyList();
        }
        List<MetricPoint> points = this.buildPoints();
        this.lastExecution = now;
        for (MetricPoint point : points) {
            point.time(now);
        }
        return points;
    }
}
