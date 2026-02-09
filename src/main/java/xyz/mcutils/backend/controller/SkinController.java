package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/skins")
@Tag(name = "Skin Controller", description = "The Skin Controller is used to get skin images.")
@Slf4j
public class SkinController {
    private final SkinService skinService;

    @Autowired
    public SkinController(SkinService skinService) {
        this.skinService = skinService;
    }

    @ResponseBody
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pagination.Page<Skin>> getCapes(
            @Parameter(
                    description = "The page of skins to get",
                    example = "1"
            ) @RequestParam(required = false, defaultValue = "1") int page
    ) {
        return ResponseEntity.ok()
                .body(skinService.getPaginatedSkins(page));
    }

    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getPlayerSkinTexture(
            @Parameter(
                    description = "The UUID or Username of the player or the skin's texture id",
                    example = "ImFascinated"
            ) @PathVariable String query
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Skin skin = this.skinService.getSkinFromTextureIdOrPlayer(query);
            byte[] texture = skinService.getSkinTexture(skin.getTextureId(), skin.getTextureUrl(), false);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(texture);
        }, Main.EXECUTOR);
    }

    @GetMapping(value = "/{query}/{type}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getPlayerSkin(
            @Parameter(
                    description = "The UUID or Username of the player",
                    example = "ImFascinated"
            ) @PathVariable String query,
            @Parameter(
                    description = "The part of the skin",
                    schema = @Schema(implementation = Skin.SkinPart.class)
            ) @PathVariable String type,
            @Parameter(
                    description = "The size of the image (height; width derived per part)",
                    example = "768"
            ) @RequestParam(required = false, defaultValue = "768") int size,
            @Parameter(
                    description = "Whether to render the skin overlay (skin layers)",
                    example = "true"
            ) @RequestParam(required = false, defaultValue = "true") boolean overlays
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Skin skin = this.skinService.getSkinFromTextureIdOrPlayer(query);
            byte[] bytes = skinService.renderSkin(skin, type, overlays, size);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        }, Main.EXECUTOR);
    }
}