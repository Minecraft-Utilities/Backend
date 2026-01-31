package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.skin.Skin;
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
            @Parameter(description = "The texture id or Player UUID/name for the Skin", example = "ImFascinated") @PathVariable String query) {
        Skin skin;
        if (query.length() == 64) { // Texture id
            skin = Skin.fromId(query);
        } else {
            skin = playerService.getPlayer(query).getPlayer().getSkin();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(skinService.getSkinImage(skin));
    }

    @GetMapping(value = "/{query}/{part}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> getPlayerSkinPart(
            @Parameter(description = "The texture id or Player UUID/name for the Skin", example = "ImFascinated") @PathVariable String query,
            @Parameter(description = "The part of the skin", example = "head") @PathVariable String part,
            @Parameter(description = "The size of the image", example = "256") @RequestParam(required = false, defaultValue = "256") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "false") @RequestParam(required = false, defaultValue = "false") boolean overlays) {
        Skin skin;
        if (query.length() == 64) { // Texture id
            skin = Skin.fromId(query);
        } else {
            skin = playerService.getPlayer(query).getPlayer().getSkin();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(skinService.getSkinPart(skin, part, overlays, size).getBytes());
    }
}
