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
import java.util.concurrent.atomic.AtomicLong;

@Service
@Getter
public class StatisticsService {

    public static StatisticsService INSTANCE;

    private final PlayerRepository playerRepository;
    private final SkinRepository skinRepository;
    private final CapeRepository capeRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;

    private final AtomicLong trackedPlayerCount = new AtomicLong();
    private final AtomicLong trackedSkinCount = new AtomicLong();
    private final AtomicLong trackedCapeCount = new AtomicLong();
    private final AtomicLong trendingSkinCount = new AtomicLong();
    private final AtomicLong nameChangesCount = new AtomicLong();

    public long getTrackedPlayerCount() {
        return this.trackedPlayerCount.get();
    }

    public long getTrackedSkinCount() {
        return this.trackedSkinCount.get();
    }

    public long getTrackedCapeCount() {
        return this.trackedCapeCount.get();
    }

    public long getTrendingSkinCount() {
        return this.trendingSkinCount.get();
    }

    public long getNameChangesCount() {
        return this.nameChangesCount.get();
    }

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
        long newValue = INSTANCE.trackedPlayerCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedSkinCount(long delta) {
        if (delta == 0) {
            return;
        }
        long newValue = INSTANCE.trackedSkinCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addTrackedCapeCount(long delta) {
        if (delta == 0) {
            return;
        }
        long newValue = INSTANCE.trackedCapeCount.addAndGet(delta);
        if (newValue % 100 == 0) {
            INSTANCE.updateStatistics();
        }
    }

    public static void addNameChangesCount(long delta) {
        if (delta == 0) {
            return;
        }
        INSTANCE.nameChangesCount.addAndGet(delta);
    }

    @PostConstruct
    public void init() {
        CompletableFuture<Long> playersFuture = CompletableFuture.supplyAsync(this.playerRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> skinsFuture = CompletableFuture.supplyAsync(this.skinRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> capesFuture = CompletableFuture.supplyAsync(this.capeRepository::count, Main.EXECUTOR);
        CompletableFuture<Long> trendingSkinsFuture = CompletableFuture.supplyAsync(() -> this.skinRepository.countTrendingSkins(VanillaSkinTextureIds.ALL), Main.EXECUTOR);
        CompletableFuture<Long> nameChangesFuture = CompletableFuture.supplyAsync(this.usernameChangeEventRepository::countNameChanges, Main.EXECUTOR);

        CompletableFuture.allOf(playersFuture, skinsFuture, capesFuture, trendingSkinsFuture, nameChangesFuture).join();

        this.trackedPlayerCount.set(playersFuture.join());
        this.trackedSkinCount.set(skinsFuture.join());
        this.trackedCapeCount.set(capesFuture.join());
        this.trendingSkinCount.set(trendingSkinsFuture.join());
        this.nameChangesCount.set(nameChangesFuture.join());
    }

    /**
     * Refreshes the cached count of skins with {@code trending_heat > 0}.
     * Called after the hourly trending-heat rebuild; avoids a per-request COUNT over ~90k+ rows.
     */
    public void refreshTrendingSkinCount() {
        this.trendingSkinCount.set(this.skinRepository.countTrendingSkins(VanillaSkinTextureIds.ALL));
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
        return new StatisticsResponse(getTrackedPlayerCount(), getTrackedSkinCount(), getTrackedCapeCount());
    }
}
