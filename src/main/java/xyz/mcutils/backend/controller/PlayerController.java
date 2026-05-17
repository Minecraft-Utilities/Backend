package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.common.IPUtils;
import xyz.mcutils.backend.model.domain.player.BasicPlayer;
import xyz.mcutils.backend.model.domain.player.FullPlayer;
import xyz.mcutils.backend.model.domain.player.PlayerType;
import xyz.mcutils.backend.model.domain.player.history.RecentUsernameChange;
import xyz.mcutils.backend.model.dto.request.PlayerViewRequest;
import xyz.mcutils.backend.model.dto.request.SubmitPlayersRequest;
import xyz.mcutils.backend.model.dto.response.SubmitPlayersResponse;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.PlayerSubmitService;
import xyz.mcutils.backend.service.PlayerViewService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/players")
@Tag(name = "Player Controller", description = "The Player Controller is used to get information about a player.")
public class PlayerController {
    private final PlayerService playerService;
    private final PlayerViewService playerViewService;
    private final PlayerSubmitService playerSubmitService;

    public PlayerController(PlayerService playerService, PlayerViewService playerViewService, PlayerSubmitService playerSubmitService) {
        this.playerService = playerService;
        this.playerViewService = playerViewService;
        this.playerSubmitService = playerSubmitService;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BasicPlayer>> searchPlayers(
            @Parameter(description = "The query to search for (username prefix, case-insensitive)", example = "ImFascinated") @RequestParam String query,
            @Parameter(description = "The type of player data to return", example = "basic") @RequestParam(defaultValue = "BASIC") PlayerType type) {
        List<BasicPlayer> entries = this.playerService.searchPlayers(query).stream()
                .map(player -> type == PlayerType.FULL ? player : BasicPlayer.from(player))
                .toList();
        return ResponseEntity.ok().body(entries);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BasicPlayer> getPlayer(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The type of player data to return", example = "basic") @RequestParam(defaultValue = "BASIC") PlayerType type) {
        FullPlayer player = FullPlayer.fromRow(this.playerService.getPlayer(id), this.playerService);
        BasicPlayer result = type == PlayerType.FULL ? player : BasicPlayer.from(player);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(result);
    }

    @GetMapping(value = "/name-changes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RecentUsernameChange>> getRecentNameChanges() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(this.playerService.getRecentNameChanges());
    }

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubmitPlayersResponse> submitPlayers(@Parameter(description = "List of player UUIDs") @Valid @RequestBody SubmitPlayersRequest request) {
        int enqueued = this.playerSubmitService.submitPlayers(request.uuids(), request.submittedBy());
        return ResponseEntity.accepted().body(new SubmitPlayersResponse(enqueued));
    }

    @Hidden // no need for this to be public
    @PostMapping(value = "/count-view", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> countPlayerView(@Valid @RequestBody PlayerViewRequest request, HttpServletRequest httpRequest) {
        this.playerViewService.countView(request, IPUtils.getRealIp(httpRequest));
        return ResponseEntity.ok().build();
    }
}