package xyz.mcutils.backend.websocket.impl;

import xyz.mcutils.backend.websocket.WebSocket;

public class NameChangeWebSocket extends WebSocket {
    public NameChangeWebSocket() {
        super("/ws/name-changes");
    }
}