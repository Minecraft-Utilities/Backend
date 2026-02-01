package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/skin")
@Tag(name = "Skin Controller", description = "The Skin Controller is used to get skin images.")
@Slf4j
public class SkinController {
    private final PlayerService playerService;
    private final SkinService skinService;

    @Autowired
    public SkinController(PlayerService playerManagerService, SkinService skinService) {
        this.playerService = playerManagerService;
        this.skinService = skinService;
    }

    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> getPlayerSkin(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String query) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(skinService.getSkinBytes(this.playerService.getPlayer(query).getPlayer().getSkin(), false));
    }

    @GetMapping(value = "/{query}/{part}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> getPlayerSkinPart(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String query,
            @Parameter(description = "The part of the skin", example = "head") @PathVariable String part,
            @Parameter(description = "The size of the image (height; width derived per part)", example = "768") @RequestParam(required = false, defaultValue = "768") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "false") @RequestParam(required = false, defaultValue = "true") boolean overlays) {
        long t0 = System.nanoTime();
        var player = this.playerService.getPlayer(query).getPlayer();
        double tPlayerMs = (System.nanoTime() - t0) / 1e6;
        long t1 = System.nanoTime();
        byte[] bytes = skinService.getSkinPart(player, part, overlays, size).getBytes();
        double tSkinPartMs = (System.nanoTime() - t1) / 1e6;
        if (log.isDebugEnabled()) {
            log.debug("Skin part request: player={}ms skinPart={}ms total={}ms",
                    String.format("%.2f", tPlayerMs), String.format("%.2f", tSkinPartMs),
                    String.format("%.2f", (System.nanoTime() - t0) / 1e6));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }
}
