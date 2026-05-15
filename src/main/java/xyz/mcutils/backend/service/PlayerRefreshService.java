package xyz.mcutils.backend.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 200;
    private static final int RATE_LIMIT = 40;

    private final Semaphore tokens = new Semaphore(RATE_LIMIT);
    private final ScheduledExecutorService refiller = Executors.newSingleThreadScheduledExecutor();
    private final MojangService mojangService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerRefreshService(MojangService mojangService, PlayerService playerService, PlayerRepository playerRepository) {
        this.mojangService = mojangService;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
        refiller.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        refiller.scheduleAtFixedRate(() -> {
            int deficit = RATE_LIMIT - tokens.availablePermits();
            if (deficit > 0) {
                tokens.release(deficit);
            }
        }, 0, 1, TimeUnit.SECONDS);
        Main.EXECUTOR.submit(() -> {
            while (running.get()) {
                try {
                    Instant cutoff = Instant.now().minus(3, ChronoUnit.HOURS);
                    Slice<PlayerRow> playerRows = this.playerRepository.findAllByLastUpdatedBeforeOrderByLastUpdatedAsc(cutoff, Pageable.ofSize(REFRESH_CHUNK_SIZE));
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    List<Future<PlayerService.PlayerUpdate>> futures = new ArrayList<>();
                    for (PlayerRow playerRow : playerRows) {
                        tokens.acquire();
                        futures.add(Main.EXECUTOR.submit(() -> {
                            if (!running.get()) return null;
                            MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                            if (token == null) return null;
                            return new PlayerService.PlayerUpdate(playerRow, token);
                        }));
                    }
                    List<PlayerService.PlayerUpdate> playerUpdates = FutureUtils.awaitAll(futures, "player refresh")
                        .stream()
                        .filter(Objects::nonNull)
                        .toList();
                    this.playerService.updatePlayers(playerUpdates);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}
