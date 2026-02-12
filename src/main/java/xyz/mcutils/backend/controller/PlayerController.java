package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.domain.player.Player;
import xyz.mcutils.backend.model.dto.request.SubmitPlayersRequest;
import xyz.mcutils.backend.model.dto.response.PlayerSearchEntry;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.PlayerSubmitService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/players")
@Tag(name = "Player Controller", description = "The Player Controller is used to get information about a player.")
public class PlayerController {
    private final PlayerService playerService;
    private final PlayerSubmitService playerSubmitService;

    @Autowired
    public PlayerController(PlayerService playerManagerService, PlayerSubmitService playerSubmitService) {
        this.playerService = playerManagerService;
        this.playerSubmitService = playerSubmitService;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PlayerSearchEntry>> searchPlayers(
            @Parameter(
                    description = "The query to search for (username prefix, case-insensitive)",
                    example = "ImFascinated"
            ) @RequestParam String query
    ) {
        List<PlayerSearchEntry> entries = this.playerService.searchPlayers(query);
        return ResponseEntity.ok()
                .body(entries);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> getPlayer(
            @Parameter(
                    description = "The UUID or Username of the player",
                    example = "ImFascinated"
            ) @PathVariable String id
    ) {
        Player player = this.playerService.getPlayer(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(player);
    }

    @GetMapping(value = "/uuid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> getPlayerUuid(
            @Parameter(
                    description = "The UUID or Username of the player",
                    example = "ImFascinated"
            ) @PathVariable String id) {
        UUID uuid = this.playerService.usernameToUuid(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(uuid);
    }

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> submitPlayers(
            @Parameter(description = "List of up to 100 players (UUID only)")
            @Valid @RequestBody SubmitPlayersRequest request) {
        playerSubmitService.submitPlayers(request.players(), request.submittedBy());
        return ResponseEntity.accepted().build();
    }
}