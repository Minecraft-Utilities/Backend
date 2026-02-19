package xyz.mcutils.backend.websocket.impl;

import org.springframework.web.socket.WebSocketSession;
import xyz.mcutils.backend.model.dto.websocket.StatisticsMessage;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;
import xyz.mcutils.backend.websocket.WebSocket;

public class StatisticsWebSocket extends WebSocket {
    public StatisticsWebSocket() {
        super("/ws/statistics");
    }

    @Override
    public void onSessionConnect(WebSocketSession session) {
        sendMessage(session, new StatisticsMessage(
                PlayerService.INSTANCE.getTrackedPlayerCount(),
                SkinService.INSTANCE.getTrackedSkinCount()
        ));
    }
}