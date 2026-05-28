package xyz.mcutils.backend.metric;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single InfluxDB line-protocol metric point.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class MetricPoint {
    private final String measurement;
    private final Map<String, String> tags = new LinkedHashMap<>();
    private final Map<String, Number> fields = new LinkedHashMap<>();
    private long timestampMillis;

    public static MetricPoint measurement(String measurement) {
        return new MetricPoint(measurement);
    }

    public MetricPoint addTag(String name, String value) {
        this.tags.put(name, value);
        return this;
    }

    public MetricPoint addField(String name, double value) {
        this.fields.put(name, value);
        return this;
    }

    public MetricPoint addField(String name, long value) {
        this.fields.put(name, value);
        return this;
    }

    public MetricPoint time(long timestampMillis) {
        this.timestampMillis = timestampMillis;
        return this;
    }
}
