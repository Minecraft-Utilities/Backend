package xyz.mcutils.backend.metric.util;

import xyz.mcutils.backend.metric.MetricPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Thread-safe counter buffer that drains accumulated counts into metric points.
 */
public final class TaggedCounterBuffer {
    private final String measurement;
    private final ConcurrentHashMap<List<String>, AtomicLong> counters = new ConcurrentHashMap<>();

    public TaggedCounterBuffer(String measurement) {
        this.measurement = measurement;
    }

    public void increment(List<String> tagValues) {
        this.increment(1L, tagValues);
    }

    public void increment(long amount, List<String> tagValues) {
        if (amount <= 0L) {
            return;
        }
        this.counters.computeIfAbsent(tagValues, ignored -> new AtomicLong()).addAndGet(amount);
    }

    public List<MetricPoint> drain(BiConsumer<MetricPoint, List<String>> tagger) {
        List<MetricPoint> points = new ArrayList<>();
        for (Map.Entry<List<String>, AtomicLong> entry : this.counters.entrySet()) {
            long value = entry.getValue().getAndSet(0L);
            if (value <= 0L) {
                continue;
            }
            MetricPoint point = MetricPoint.measurement(this.measurement).addField("value", value);
            tagger.accept(point, entry.getKey());
            points.add(point);
        }
        return points;
    }
}
