package xyz.mcutils.backend.websocket;

import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketManager implements WebSocketConfigurer {
    private static final List<WebSocket> WEB_SOCKETS = new ArrayList<>();

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {

    }

    /**
     * Registers a WebSocket.
     *
     * @param registry the registry to register the WebSocket on
     * @param webSocket the WebSocket to register
     */
    private void registerWebSocket(WebSocketHandlerRegistry registry, WebSocket webSocket) {
        registry.addHandler(webSocket, webSocket.getPath()).setAllowedOrigins("*");
        WEB_SOCKETS.add(webSocket);
    }

    /**
     * Gets the total amount of connections.
     *
     * @return the total amount of connections
     */
    public static int getTotalConnections() {
        return WEB_SOCKETS.stream().mapToInt(webSocket -> webSocket.getSessions().size()).sum();
    }
}
