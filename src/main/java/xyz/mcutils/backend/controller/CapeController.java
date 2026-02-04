package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.PlayerService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/cape")
@Tag(name = "Cape Controller", description = "The Cape Controller is used to get cape images.")
public class CapeController {
    private final CapeService capeService;
    private final PlayerService playerService;

    @Autowired
    public CapeController(CapeService capeService, PlayerService playerService) {
        this.capeService = capeService;
        this.playerService = playerService;
    }

    @ResponseBody
    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getCapeTexture(
            @Parameter(
                    description = "The UUID or Username of the player or the skin's texture id",
                    example = "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"
            ) @PathVariable String query) {
        return CompletableFuture.supplyAsync(() -> {
            Cape cape;
            if (query.length() == 64) {
                cape = Cape.fromId(query);
            } else {
                cape = playerService.getPlayer(query).getPlayer().getCape();
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(capeService.getCapeTexture(cape));
        }, Main.EXECUTOR);
    }
}