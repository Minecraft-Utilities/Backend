package xyz.mcutils.backend.metric.impl.websocket;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.websocket.WebSocketManager;

import java.util.concurrent.TimeUnit;

/**
 * Exposes the total number of active WebSocket sessions across all registered handlers.
 */
public class WebSocketConnectionsMetric extends Metric {
    public WebSocketConnectionsMetric() {
        super(TimeUnit.SECONDS.toMillis(2L));
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("websocket_connections_active")
                .addField("value", WebSocketManager.getTotalConnections());
    }
}
