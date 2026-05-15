package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.repository.postgres.CapeRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.StatisticsWebSocket;

@Service
@Getter
public class StatisticsService {

    public static StatisticsService INSTANCE;

    private final PlayerRepository playerRepository;
    private final SkinRepository skinRepository;
    private final CapeRepository capeRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;

    private long trackedPlayerCount;
    private long trackedSkinCount;
    private long trackedCapeCount;
    private long nameChangesCount;

    public StatisticsService(PlayerRepository playerRepository, SkinRepository skinRepository, CapeRepository capeRepository, UsernameChangeEventRepository usernameChangeEventRepository) {
        this.playerRepository = playerRepository;
        this.skinRepository = skinRepository;
        this.capeRepository = capeRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        INSTANCE = this;
    }

    public static void addTrackedPlayerCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.trackedPlayerCount += delta;
        if (INSTANCE.trackedPlayerCount % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedSkinCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.trackedSkinCount += delta;
        if (INSTANCE.trackedSkinCount % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedCapeCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.trackedCapeCount += delta;
        if (INSTANCE.trackedCapeCount % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addNameChangesCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.nameChangesCount += delta;
    }

    @PostConstruct
    public void init() {
        this.trackedPlayerCount = this.playerRepository.count();
        this.trackedSkinCount = this.skinRepository.count();
        this.trackedCapeCount = this.capeRepository.count();
        this.nameChangesCount = this.usernameChangeEventRepository.countNameChanges();
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
        return new StatisticsResponse(trackedPlayerCount, trackedSkinCount, trackedCapeCount);
    }
}
