package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
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
public class SkinController {
    private final PlayerService playerService;
    private final SkinService skinService;

    @Autowired
    public SkinController(PlayerService playerManagerService, SkinService skinService) {
        this.playerService = playerManagerService;
        this.skinService = skinService;
    }

    @GetMapping(value = "/texture/{query}.png", produces = MediaType.IMAGE_PNG_VALUE)
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
            @Parameter(description = "The size of the image (height; width derived per part)", example = "512") @RequestParam(required = false, defaultValue = "512") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "false") @RequestParam(required = false, defaultValue = "true") boolean overlays) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(skinService.getSkinPart(this.playerService.getPlayer(query).getPlayer(), part, overlays, size).getBytes());
    }
}
