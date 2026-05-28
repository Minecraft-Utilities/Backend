package xyz.mcutils.backend.metric.impl.jvm;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Exposes cumulative GC collection count and total time per collector.
 */
public class GcMetric extends Metric {
    public GcMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    @Nullable
    public MetricPoint buildPoint() {
        return null;
    }

    @Override
    public List<MetricPoint> buildPoints() {
        List<MetricPoint> points = new ArrayList<>(Constants.GC_BEANS.size() * 2);
        for (var gc : Constants.GC_BEANS) {
            points.add(MetricPoint.measurement("jvm_gc_collection_count_total")
                    .addTag("gc", gc.getName())
                    .addField("value", gc.getCollectionCount()));
            points.add(MetricPoint.measurement("jvm_gc_collection_seconds_total")
                    .addTag("gc", gc.getName())
                    .addField("value", gc.getCollectionTime() / 1000.0));
        }
        return points;
    }
}
