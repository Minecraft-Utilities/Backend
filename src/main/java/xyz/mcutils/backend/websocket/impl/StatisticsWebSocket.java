package xyz.mcutils.backend.websocket.impl;

import org.springframework.web.socket.WebSocketSession;
import xyz.mcutils.backend.model.dto.websocket.StatisticsMessage;
import xyz.mcutils.backend.service.StatisticsService;
import xyz.mcutils.backend.websocket.WebSocket;

public class StatisticsWebSocket extends WebSocket {
    public StatisticsWebSocket() {
        super("/ws/statistics");
    }

    @Override
    public void onSessionConnect(WebSocketSession session) {
        sendMessage(session, new StatisticsMessage(StatisticsService.INSTANCE.getTrackedPlayerCount(), StatisticsService.INSTANCE.getTrackedSkinCount()));
    }

    /**
     * Updates the statistics for the all connected WebSocket clients.
     */
    public void updateStatistics() {
        sendMessageToAll(new StatisticsMessage(StatisticsService.INSTANCE.getTrackedPlayerCount(), StatisticsService.INSTANCE.getTrackedSkinCount()));
    }
}