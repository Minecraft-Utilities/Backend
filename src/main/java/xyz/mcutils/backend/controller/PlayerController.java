package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayer;
import xyz.mcutils.backend.model.persistence.redis.CachedPlayerName;
import xyz.mcutils.backend.service.PlayerService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/players")
@Tag(name = "Player Controller", description = "The Player Controller is used to get information about a player.")
public class PlayerController {
    private final PlayerService playerService;

    @Autowired
    public PlayerController(PlayerService playerManagerService) {
        this.playerService = playerManagerService;
    }

    @ResponseBody
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<CachedPlayer>> getPlayer(
            @Parameter(
                    description = "The UUID or Username of the player",
                    example = "ImFascinated"
            ) @PathVariable String id
    ) {
        return CompletableFuture.supplyAsync(() -> playerService.getPlayer(id), Main.EXECUTOR)
                .thenApply(player -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                        .body(player));
    }

    @ResponseBody
    @GetMapping(value = "/uuid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<CachedPlayerName>> getPlayerUuid(
            @Parameter(
                    description = "The UUID or Username of the player",
                    example = "ImFascinated"
            ) @PathVariable String id) {
        return CompletableFuture.supplyAsync(() -> playerService.usernameToUuid(id), Main.EXECUTOR)
                .thenApply(playerName -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                        .body(playerName));
    }
}