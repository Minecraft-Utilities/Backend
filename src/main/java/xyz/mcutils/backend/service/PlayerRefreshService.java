package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerRefreshService {
    private static final int REFRESH_CHUNK_SIZE = 500;
    private static final int RATE_LIMIT = 200;

    private final RateLimiter rateLimiter = RateLimiter.create(RATE_LIMIT);
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
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRefreshTask() {
        Main.EXECUTOR.submit(() -> {
            while (running.get()) {
                try {
                    Instant cutoff = Instant.now().minus(PlayerService.PLAYER_UPDATE_INTERVAL);
                    Slice<PlayerRow> playerRows = this.playerRepository.findAllByLastUpdatedBeforeOrderByLastUpdatedAsc(
                            cutoff,
                            Pageable.ofSize(REFRESH_CHUNK_SIZE)
                    );
                    if (playerRows.isEmpty()) {
                        Thread.sleep(Duration.ofSeconds(10));
                        continue;
                    }
                    List<Future<PlayerService.PlayerUpdate>> futures = new ArrayList<>();
                    for (PlayerRow playerRow : playerRows) {
                        rateLimiter.acquire();
                        futures.add(Main.EXECUTOR.submit(() -> {
                            if (!running.get()) {
                                return null;
                            }
                            MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                            if (token == null) {
                                return null;
                            }
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
