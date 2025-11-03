package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.response.SkinResponse;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;

import java.util.List;
import java.util.concurrent.TimeUnit;
    
@RestController
@RequestMapping(value = "/skin")
@Tag(name = "Skin", description = "Skin-related endpoints")
public class SkinController {
    private final SkinService skinService;
    private final PlayerService playerService;

    public SkinController(SkinService skinService, PlayerService playerService) {
        this.skinService = skinService;
        this.playerService = playerService;
    }

    @GetMapping(value = "/{id}.{extension}")
    public ResponseEntity<?> getPlayerSkin(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The file extension of the image", example = "png") @PathVariable String extension,
            @Parameter(description = "Whether to download the image") @RequestParam(required = false, defaultValue = "false") boolean download) {
        CachedPlayer cachedPlayer = playerService.getPlayer(id);
        String dispositionHeader = download ? "attachment; filename=%s.png" : "inline; filename=%s.png";

        // Return the part image
        Skin skin = cachedPlayer.getPlayer().getSkin();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionHeader.formatted(skin.getId()))
                .body(skinService.getSkinImage(skin));
    }
}
