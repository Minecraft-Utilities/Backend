package xyz.mcutils.backend.metric.impl.websocket;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.websocket.WebSocketManager;

/**
 * Exposes the total number of active WebSocket sessions across all registered handlers.
 */
public class WebSocketConnectionsMetric extends Metric<WebSocketConnectionsMetric.Holder> {

    public WebSocketConnectionsMetric() {
        super(new Holder(
                GaugeWithCallback.builder()
                        .name("websocket_connections_active")
                        .help("Current number of active WebSocket sessions across all handlers")
                        .callback(callback -> callback.call(WebSocketManager.getTotalConnections()))
                        .register(MetricService.REGISTRY)
        ));
    }

    public record Holder(GaugeWithCallback gauge) {}
}
