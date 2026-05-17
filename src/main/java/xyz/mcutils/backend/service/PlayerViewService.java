package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.domain.player.FullPlayer;
import xyz.mcutils.backend.model.dto.request.PlayerViewRequest;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;
import xyz.mcutils.backend.model.token.turnstile.TurnstileResponse;
import xyz.mcutils.backend.repository.postgres.PlayerViewEventRepository;

import java.time.Instant;

@Service
public class PlayerViewService {

    @Value("${mc-utils.player-views.auth-token}")
    public String authToken;

    private final PlayerService playerService;
    private final TurnstileService turnstileService;
    private final PlayerViewEventRepository playerViewEventRepository;

    public PlayerViewService(PlayerService playerService, TurnstileService turnstileService, PlayerViewEventRepository playerViewEventRepository) {
        this.playerService = playerService;
        this.turnstileService = turnstileService;
        this.playerViewEventRepository = playerViewEventRepository;
    }

    public void countView(PlayerViewRequest request, String ip) {
        if (authToken == null) {
            throw new IllegalStateException("Player view auth token has not been set");
        }

        TurnstileResponse turnstileResponse = this.turnstileService.validateToken(request.turnstileToken(), ip);
        if (!turnstileResponse.isSuccess()) {
            throw new BadRequestException("Invalid Turnstile Token");
        }
        // todo: only count an ip once per 30d
        FullPlayer player = this.playerService.getPlayer(request.playerQuery());
        this.playerViewEventRepository.save(new PlayerViewEventRow(player.getUniqueId(), ip, Instant.now()));
    }
}
