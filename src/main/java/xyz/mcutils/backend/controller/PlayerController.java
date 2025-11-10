package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/player")
@Tag(name = "Player Controller", description = "The Player Controller is used to get information about a player.")
public class PlayerController {
    private final PlayerService playerService;
    private final SkinService skinService;

    @Autowired
    public PlayerController(PlayerService playerManagerService, SkinService skinService) {
        this.playerService = playerManagerService;
        this.skinService = skinService;
    }

    @ResponseBody
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPlayer(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id) {
        CachedPlayer player = playerService.getPlayer(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(player);
    }

    @ResponseBody
    @GetMapping(value = "/uuid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CachedPlayerName> getPlayerUuid(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id) {
        CachedPlayerName cachedPlayerName = playerService.usernameToUuid(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(cachedPlayerName);
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

    @GetMapping(value = "/{id}/skin/{part}.{extension}")
    public ResponseEntity<?> getPlayerSkinPart(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The part of the skin", example = "head") @PathVariable String part,
            @Parameter(description = "The file extension of the image", example = "png") @PathVariable String extension,
            @Parameter(description = "The size of the image", example = "256") @RequestParam(required = false, defaultValue = "256") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "false") @RequestParam(required = false, defaultValue = "false") boolean overlays,
            @Parameter(description = "Whether to download the image") @RequestParam(required = false, defaultValue = "false") boolean download) {
        CachedPlayer cachedPlayer
                = playerService.getPlayer(id);
        String dispositionHeader = download ? "attachment; filename=%s.png" : "inline; filename=%s.png";

        // Return the part image
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionHeader.formatted(cachedPlayer.getPlayer().getUsername()))
                .body(skinService.getSkinPart(cachedPlayer.getPlayer(), part, overlays, size).getBytes());
    }
}
