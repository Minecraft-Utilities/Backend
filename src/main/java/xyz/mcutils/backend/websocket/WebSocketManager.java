package xyz.mcutils.backend.websocket;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import xyz.mcutils.backend.websocket.impl.TrackedAccountsWebsocket;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
@Log4j2
public class WebSocketManager implements WebSocketConfigurer {
    private static final List<WebSocket> WEB_SOCKETS = new ArrayList<>();

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {
        this.registerWebSocket(registry, new TrackedAccountsWebsocket());
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
        log.info("Registered WebSocket at path {}", webSocket.getPath());
    }

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
}
