package xyz.mcutils.backend.websocket;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
@Getter
@Slf4j
public abstract class WebSocket extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    /**
     * The path of the WebSocket.
     * <p>
     * Example: /websocket/metrics
     * </p>
     */
    public final String path;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    @Setter
    private JsonMapper jsonMapper;

    /**
     * Sends a message to the client.
     *
     * @param session the session to send the message to
     * @param message the message to send
     */
    @SneakyThrows
    public void sendMessage(WebSocketSession session, Object message) {
        session.sendMessage(new TextMessage(message instanceof String ? (String) message : this.jsonMapper.writeValueAsString(message)));
    }

    /**
     * Sends a message to all connected clients.
     * <p>
     * Serializes the payload once and shares the {@link TextMessage} across sessions
     * (previously the payload was serialized once per client). Each send is guarded so a
     * single closed/slow session cannot abort the fan-out to the remaining clients.
     *
     * @param message the message to send
     */
    public void sendMessageToAll(Object message) {
        if (this.sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(message instanceof String ? (String) message : this.jsonMapper.writeValueAsString(message));
        for (WebSocketSession session : this.sessions) {
            try {
                session.sendMessage(textMessage);
            } catch (Exception e) {
                log.warn("Failed to send message to session {} on {}: {}", session.getId(), this.path, e.toString());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignored) {
                    // session is already gone; afterConnectionClosed will remove it
                }
            }
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
        // Pushes arrive from every refresh worker at once, and a raw session rejects
        // overlapping writes — which the fan-out below answers by closing the client.
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
        this.sessions.add(concurrentSession);
        log.info("Connection established on {} ({})", this.getPath(), session.getId());
        this.onSessionConnect(concurrentSession);
    }

    @Override
    public final void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        // The container hands back the raw session, while the list holds its decorator.
        this.sessions.removeIf(connected -> connected.getId().equals(session.getId()));
        log.info("Connection closed on {} ({})", this.getPath(), session.getId());
    }
}