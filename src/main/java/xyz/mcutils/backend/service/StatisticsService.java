package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;
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
        if (count % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    /**
     * Incremental update to avoid Mongo estimatedCount on every bulk create.
     */
    public static void addTrackedPlayerCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.trackedPlayerCount += delta;
        if (INSTANCE.trackedPlayerCount % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void updateTrackedSkinCount(long count) {
        INSTANCE.trackedSkinCount = count;
        if (count % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void updateTrackedCapeCount(long count) {
        INSTANCE.trackedCapeCount = count;
        if (count % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    /**
     * Updates the statistics for the all connected WebSocket clients.
     */
    public void updateStatistics() {
        ((StatisticsWebSocket) WebSocketManager.getWebsocket(StatisticsWebSocket.class)).updateStatistics();
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
