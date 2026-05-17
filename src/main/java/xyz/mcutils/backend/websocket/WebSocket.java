package xyz.mcutils.backend.websocket;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xyz.mcutils.backend.Constants;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Getter
@Slf4j
public abstract class WebSocket extends TextWebSocketHandler {

    /**
     * The path of the WebSocket.
     * <p>
     * Example: /websocket/metrics
     * </p>
     */
    public final String path;
    /**
     * The sessions that are connected to the WebSocket.
     */
    private final List<WebSocketSession> sessions = new ArrayList<>();

    /**
     * Sends a message to the client.
     *
     * @param session the session to send the message to
     * @param message the message to send
     */
    @SneakyThrows
    public void sendMessage(WebSocketSession session, Object message) {
        session.sendMessage(new TextMessage(message instanceof String ? (String) message : Constants.GSON.toJson(message)));
    }

    /**
     * Sends a message to all connected clients.
     *
     * @param message the message to send
     */
    public void sendMessageToAll(Object message) {
        for (WebSocketSession session : this.sessions) {
            this.sendMessage(session, message);
        }
    }

    /**
     * Called when a session connects to the WebSocket.
     *
     * @param session the session that connected
     */
    public void onSessionConnect(WebSocketSession session) {}

    @Override
    public final void afterConnectionEstablished(@NotNull WebSocketSession session) {
        this.sessions.add(session);
        log.info("Connection established: {}", session.getId());
        this.onSessionConnect(session);
    }

    @Override
    public final void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        this.sessions.remove(session);
        log.info("Connection closed: {}", session.getId());
    }
}