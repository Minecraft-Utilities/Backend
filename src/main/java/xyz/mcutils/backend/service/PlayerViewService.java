package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.IPUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.dto.request.PlayerViewRequest;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;
import xyz.mcutils.backend.model.token.turnstile.TurnstileResponse;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.PlayerViewEventRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class PlayerViewService {

    @Value("${mc-utils.player-views.auth-token}")
    public String authToken;

    @Value("${mc-utils.player-views.ip-salt}")
    public String ipSalt;

    private final PlayerService playerService;
    private final TurnstileService turnstileService;
    private final PlayerRepository playerRepository;
    private final PlayerViewEventRepository playerViewEventRepository;

    public PlayerViewService(PlayerService playerService, TurnstileService turnstileService, PlayerRepository playerRepository,
                             PlayerViewEventRepository playerViewEventRepository) {
        this.playerService = playerService;
        this.turnstileService = turnstileService;
        this.playerRepository = playerRepository;
        this.playerViewEventRepository = playerViewEventRepository;
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void updateTrendingHeat() {
        long before = System.currentTimeMillis();
        this.playerRepository.resetMonthlyViews();
        this.playerRepository.updateMonthlyViews();
        log.info("Updated monthly views for players in {}ms", System.currentTimeMillis() - before);
    }

    public void countView(PlayerViewRequest request, String ip) {
        if (authToken == null) {
            throw new IllegalStateException("Player view auth token has not been set");
        }

        String ipHash = IPUtils.hashIp(ip, ipSalt);
        TurnstileResponse turnstileResponse = this.turnstileService.validateToken(request.turnstileToken(), ip);
        if (!turnstileResponse.isSuccess()) {
            throw new BadRequestException("Invalid Turnstile Token");
        }
        PlayerRow player = this.playerService.getPlayer(request.playerQuery());
        boolean alreadyViewed = playerViewEventRepository.existsByPlayerIdAndIpAddressAndViewedAtAfter(player.getId(), ipHash,
                Instant.now().minus(30, ChronoUnit.DAYS));
        if (!alreadyViewed) {
            this.playerViewEventRepository.save(new PlayerViewEventRow(player.getId(), ipHash, Instant.now()));
        }
    }
}
