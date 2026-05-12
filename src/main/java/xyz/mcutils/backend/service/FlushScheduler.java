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

    public FlushScheduler(PlayerManager playerManager, SkinManager skinManager, CapeManager capeManager) {
        this.playerManager = playerManager;
        this.skinManager = skinManager;
        this.capeManager = capeManager;
    }

    @Scheduled(fixedRate = 60_000 * 10)
    public void flushCaches() {
        this.playerManager.flush();
        this.skinManager.flush();
        this.capeManager.flush();
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Shutdown: flushing caches...");
        flushCaches();
        log.info("Shutdown: caches flushed.");
    }
}
