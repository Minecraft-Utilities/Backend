package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.domain.skin.SkinLookupSort;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/skins")
@Tag(name = "Skin Controller", description = "The Skin Controller is used to get skin images.")
@Slf4j
public class SkinController {
    private final SkinService skinService;
    private final CapeService capeService;

    @Autowired
    public SkinController(SkinService skinService, CapeService capeService) {
        this.skinService = skinService;
        this.capeService = capeService;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pagination.Page<Skin>> getSkins(
            @Parameter(description = "The page of skins to get", example = "1") @RequestParam(required = false, defaultValue = "1") int page,
            @Parameter(description = "The sort order", schema = @Schema(implementation = SkinLookupSort.class)) @RequestParam(required = false, defaultValue = "TRENDING") SkinLookupSort sort) {
        return ResponseEntity.ok().body(skinService.getPaginatedSkins(page, sort));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Skin> getSkin(@Parameter(description = "The UUID of the skin") @PathVariable Long id) {
        return ResponseEntity.ok().body(skinService.getSkinById(id));
    }

    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getPlayerSkinTexture(@Parameter(description = "The UUID or Username of the player or the skin's texture id", example = "ImFascinated") @PathVariable String query, @Parameter(description = "Whether to upgrade the skin to the modern format", example = "true") @RequestParam(required = false, defaultValue = "true") boolean upgrade) {
        Skin skin = Skin.fromRow(this.skinService.getSkinByQuery(query));
        byte[] texture = skinService.getSkinTexture(skin.getTextureId(), skin.getRawTextureUrl(), upgrade);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()).contentType(MediaType.IMAGE_PNG).body(texture);
    }

    @GetMapping(value = "/{query}/{type}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getPlayerSkin(@Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String query, @Parameter(description = "The part of the skin", schema = @Schema(implementation = Skin.SkinPart.class)) @PathVariable String type, @Parameter(description = "The size of the image (height; width derived per part)", example = "768") @RequestParam(required = false, defaultValue = "768") int size, @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "true") @RequestParam(required = false, defaultValue = "true") boolean overlays, @Parameter(description = "The texture ID of a cape to render alongside the skin (only applies to full-body isometric parts)") @RequestParam(required = false) @Nullable String capeId) {
        Skin skin = Skin.fromRow(this.skinService.getSkinByQuery(query));
        VanillaCape cape = (capeId != null && !capeId.trim().isEmpty()) ? VanillaCape.fromRow(this.capeService.getCapeByQuery(capeId)) : null;
        RenderOptions options = new RenderOptions(overlays, cape);
        byte[] bytes = skinService.renderSkin(skin, type, options, size);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()).contentType(MediaType.IMAGE_PNG).body(bytes);
    }
}