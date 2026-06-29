package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.domain.skin.VanillaSkinTextureIds;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.repository.postgres.CapeRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.StatisticsWebSocket;

import java.util.concurrent.CompletableFuture;

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
    private long trendingSkinCount;
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
        CompletableFuture<Long> playersFuture = CompletableFuture.supplyAsync(this.playerRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> skinsFuture = CompletableFuture.supplyAsync(this.skinRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> capesFuture = CompletableFuture.supplyAsync(this.capeRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> trendingSkinsFuture = CompletableFuture.supplyAsync(() -> this.skinRepository.countTrendingSkins(VanillaSkinTextureIds.ALL), Main.EXECUTOR);
        CompletableFuture<Long> nameChangesFuture = CompletableFuture.supplyAsync(this.usernameChangeEventRepository::countNameChanges, Main.EXECUTOR);

        CompletableFuture.allOf(playersFuture, skinsFuture, capesFuture, trendingSkinsFuture, nameChangesFuture).join();

        this.trackedPlayerCount = playersFuture.join();
        this.trackedSkinCount = skinsFuture.join();
        this.trackedCapeCount = capesFuture.join();
        this.trendingSkinCount = trendingSkinsFuture.join();
        this.nameChangesCount = nameChangesFuture.join();
    }

    /**
     * Refreshes the cached count of skins with {@code trending_heat > 0}.
     * Called after the hourly trending-heat rebuild; avoids a per-request COUNT over ~90k+ rows.
     */
    public void refreshTrendingSkinCount() {
        this.trendingSkinCount = this.skinRepository.countTrendingSkins(VanillaSkinTextureIds.ALL);
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
