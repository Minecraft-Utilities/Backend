package xyz.mcutils.backend.service;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.StatisticsWebSocket;

@Service @Getter
public class StatisticsService {

    private final PlayerService playerService;
    private final SkinService skinService;
    private final CapeService capeService;

    public static StatisticsService INSTANCE;

    private long trackedPlayerCount;
    private long trackedSkinCount;
    private long trackedCapeCount;

    public StatisticsService(PlayerService playerService, SkinService skinService, CapeService capeService) {
        this.playerService = playerService;
        this.skinService = skinService;
        this.capeService = capeService;
        INSTANCE = this;
    }

    @PostConstruct
    public void init() {
        this.trackedPlayerCount = playerService.getTrackedPlayerCount();
        this.trackedSkinCount = skinService.getTrackedSkinCount();
        this.trackedCapeCount = capeService.getTrackedCapeCount();
    }

    public static void updateTrackedPlayerCount(long count) {
        INSTANCE.trackedPlayerCount = count;
        INSTANCE.updateStatistics();
    }

    public static void updateTrackedSkinCount(long count) {
        INSTANCE.trackedSkinCount = count;
        INSTANCE.updateStatistics();
    }

    public static void updateTrackedCapeCount(long count) {
        INSTANCE.trackedCapeCount = count;
        INSTANCE.updateStatistics();
    }

    /**
     * Updates the statistics for the all connected WebSocket clients.
     */
    public void updateStatistics() {
        StatisticsWebSocket websocket = (StatisticsWebSocket) WebSocketManager.getWebsocket(StatisticsWebSocket.class);
        if (websocket != null) {
            websocket.updateStatistics();
        }
    }

    /**
     * Gets the statistics for the app.
     *
     * @return the statistics
     */
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(
                trackedPlayerCount,
                trackedSkinCount,
                trackedCapeCount
        );
    }
}
