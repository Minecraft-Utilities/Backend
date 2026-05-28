package xyz.mcutils.backend.metric;

import java.util.List;
import java.util.Map;

/**
 * Serializes {@link MetricPoint}'s to InfluxDB line protocol.
 */
public final class InfluxLineProtocol {
    private InfluxLineProtocol() {
    }

    public static String format(List<MetricPoint> points) {
        StringBuilder builder = new StringBuilder();
        for (MetricPoint point : points) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            appendPoint(builder, point);
        }
        return builder.toString();
    }

    private static void appendPoint(StringBuilder builder, MetricPoint point) {
        builder.append(escapeTag(point.getMeasurement()));
        for (Map.Entry<String, String> tag : point.getTags().entrySet()) {
            builder.append(',')
                    .append(escapeTag(tag.getKey()))
                    .append('=')
                    .append(escapeTag(tag.getValue()));
        }
        builder.append(' ');
        boolean firstField = true;
        for (Map.Entry<String, Number> field : point.getFields().entrySet()) {
            if (!firstField) {
                builder.append(',');
            }
            firstField = false;
            builder.append(escapeTag(field.getKey())).append('=');
            Number value = field.getValue();
            if (value instanceof Long || value instanceof Integer) {
                builder.append(value.longValue()).append('i');
            } else {
                builder.append(value.doubleValue());
            }
        }
        if (point.getTimestampMillis() > 0L) {
            builder.append(' ').append(point.getTimestampMillis());
        }
    }

    private static String escapeTag(String value) {
        return value.replace("\\", "\\\\")
                .replace(" ", "\\ ")
                .replace(",", "\\,")
                .replace("=", "\\=");
    }
}
