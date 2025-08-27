package xyz.mcutils.backend.controller;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.response.SkinResponse;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.SkinService;
    
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
    public ResponseEntity<?> getSkin(
            @Parameter(description = "The ID of the skin", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The file extension of the image", example = "png") @PathVariable String extension,
            @Parameter(description = "Whether to download the image") @RequestParam(required = false, defaultValue = "false") boolean download) {
        String dispositionHeader = download ? "attachment; filename=%s.png" : "inline; filename=%s.png";

        Skin skin = skinService.getSkin(id);
        if (skin == null) {
            return ResponseEntity.notFound().build();
        }

        // Return the part image
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionHeader.formatted(skin.getId()))
                .body(skinService.getSkinImage(skin));
    }

    @GetMapping(value = "/by-player/{id}.{extension}")
    public ResponseEntity<?> getPlayerSkin(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The file extension of the image", example = "png") @PathVariable String extension,
            @Parameter(description = "Whether to download the image") @RequestParam(required = false, defaultValue = "false") boolean download) {
        Player player = playerService.getPlayer(id, true);
        String dispositionHeader = download ? "attachment; filename=%s.png" : "inline; filename=%s.png";

        // Return the part image
        SkinResponse skin = player.getCurrentSkin();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionHeader.formatted(skin.getId()))
                .body(skinService.getSkinImage(skin));
    }

    @GetMapping("/popular")
    @Operation(summary = "Get most popular skins", description = "Returns the most popular skins based on current usage count")
    public ResponseEntity<List<SkinService.PopularSkinInfo>> getMostPopularSkins(
            @Parameter(description = "Maximum number of skins to return (1-100)", example = "10") 
            @RequestParam(defaultValue = "10") int limit) {
        
        List<SkinService.PopularSkinInfo> popularSkins = skinService.getMostPopularSkins(limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic())
                .body(popularSkins);
    }
}
