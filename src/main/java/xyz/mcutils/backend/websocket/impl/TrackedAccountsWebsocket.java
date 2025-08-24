package xyz.mcutils.backend.websocket.impl;

import org.springframework.web.socket.WebSocketSession;
import xyz.mcutils.backend.model.websocket.TrackedAccountsMessage;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.websocket.WebSocket;

public class TrackedAccountsWebsocket extends WebSocket {
    public TrackedAccountsWebsocket() {
        super("/websocket/tracked-accounts");
    }

    @Override
    public void onSessionConnect(WebSocketSession session) {
        sendMessage(session, new TrackedAccountsMessage(PlayerService.INSTANCE.getTotalPlayers()));
    }
}
