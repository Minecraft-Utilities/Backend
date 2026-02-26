package xyz.mcutils.backend.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.cape.CapeManager;
import xyz.mcutils.backend.player.PlayerManager;
import xyz.mcutils.backend.skin.SkinManager;

/**
 * Periodically flushes dirty player, skin, and cape cache entries to MongoDB.
 */
@Service
@Slf4j
public class FlushScheduler {
    private final PlayerManager playerManager;
    private final SkinManager skinManager;
    private final CapeManager capeManager;
    private final PlayerSubmitService playerSubmitService;
    private final PlayerRefreshService playerRefreshService;

    public FlushScheduler(PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager,
                          PlayerSubmitService playerSubmitService, PlayerRefreshService playerRefreshService) {
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
        this.playerSubmitService = playerSubmitService;
        this.playerRefreshService = playerRefreshService;
    }

    @Scheduled(fixedRate = 60_000 * 4)
    public void flushCaches() {
        this.playerManager.flush();
        this.skinManager.flush();
        this.capeManager.flush();
    }

    /**
     * On shutdown, stop submit/refresh loops, then flush all caches and wait until finished.
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Shutdown: stopping submit and refresh...");
        playerSubmitService.stop();
        playerRefreshService.stop();
        log.info("Shutdown: flushing caches...");
        flushCaches();
        log.info("Shutdown: caches flushed.");
    }
}
