package xyz.mcutils.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import tools.jackson.databind.json.JsonMapper;

import xyz.mcutils.backend.websocket.impl.NameChangeWebSocket;
import xyz.mcutils.backend.websocket.impl.StatisticsWebSocket;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
@Slf4j
@RequiredArgsConstructor
public class WebSocketManager implements WebSocketConfigurer {
    private static final List<WebSocket> WEB_SOCKETS = new ArrayList<>();

    private final JsonMapper jsonMapper;

    /**
     * Gets a WebSocket by its class.
     *
     * @param clazz the class of the WebSocket
     * @return the WebSocket or null if not found
     */
    public static WebSocket getWebsocket(Class<? extends WebSocket> clazz) {
        return WEB_SOCKETS.stream().filter(webSocket -> webSocket.getClass().equals(clazz)).findFirst().orElse(null);
    }

    /**
     * Gets the total amount of connections.
     *
     * @return the total amount of connections
     */
    public static int getTotalConnections() {
        return WEB_SOCKETS.stream().mapToInt(webSocket -> webSocket.getSessions().size()).sum();
    }

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {
        this.registerWebSocket(registry, new StatisticsWebSocket());
        this.registerWebSocket(registry, new NameChangeWebSocket());
    }

    /**
     * Registers a WebSocket.
     *
     * @param registry  the registry to register the WebSocket on
     * @param webSocket the WebSocket to register
     */
    private void registerWebSocket(WebSocketHandlerRegistry registry, WebSocket webSocket) {
        webSocket.setJsonMapper(jsonMapper);
        registry.addHandler(webSocket, webSocket.getPath()).setAllowedOrigins("*");
        WEB_SOCKETS.add(webSocket);
        log.info("Registered WebSocket at path {}", webSocket.getPath());
    }
}