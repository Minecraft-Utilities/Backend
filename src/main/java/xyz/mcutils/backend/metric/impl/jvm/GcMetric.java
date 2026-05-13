package xyz.mcutils.backend.metric.impl.jvm;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Exposes cumulative GC collection count and total time per collector.
 * Use rate() in Prometheus/VictoriaMetrics to derive GC rate and pause time rate.
 */
public class GcMetric extends Metric<GcMetric.Holder> {
    public GcMetric() {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("jvm_gc_collection_count_total")
                        .help("Total number of GC collections by collector")
                        .labelNames("gc")
                        .callback(callback -> {
                            for (var gc : Constants.GC_BEANS) {
                                callback.call(gc.getCollectionCount(), gc.getName());
                            }
                        })
                        .register(MetricService.REGISTRY),
                GaugeWithCallback.builder()
                        .name("jvm_gc_collection_seconds_total")
                        .help("Total time spent in GC by collector, in seconds")
                        .labelNames("gc")
                        .callback(callback -> {
                            for (var gc : Constants.GC_BEANS) {
                                callback.call(gc.getCollectionTime() / 1000.0, gc.getName());
                            }
                        })
                        .register(MetricService.REGISTRY)
        ));
    }

    public record Holder(GaugeWithCallback count, GaugeWithCallback seconds) {}
}
