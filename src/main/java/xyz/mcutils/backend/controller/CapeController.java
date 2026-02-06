package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.model.cape.CapeRendererType;
import xyz.mcutils.backend.model.cape.impl.VanillaCape;
import xyz.mcutils.backend.service.CapeService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/capes")
@Tag(name = "Cape Controller", description = "The Cape Controller is used to get cape images.")
public class CapeController {
    private final CapeService capeService;

    @Autowired
    public CapeController(CapeService capeService) {
        this.capeService = capeService;
    }

    @ResponseBody
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<VanillaCape>> getCapes() {
        return ResponseEntity.ok()
                .body(new ArrayList<>(capeService.getCapes().values()));
    }

    @ResponseBody
    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getCapeTexture(
            @Parameter(
                    description = "The UUID or Username of the player or the skin's texture id",
                    example = "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"
            ) @PathVariable String query) {
        return CompletableFuture.supplyAsync(() -> {
            Cape cape = this.capeService.getCapeFromTextureIdOrPlayer(query);
            byte[] bytes = capeService.getCapeTexture(cape);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(bytes);
        }, Main.EXECUTOR);
    }

    @ResponseBody
    @GetMapping(value = "/{query}/{type}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getCapePart(
            @Parameter(
                    description = "The UUID or Username of the player or the cape's texture id",
                    example = "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"
            ) @PathVariable String query,
            @Parameter(
                    description = "The part of the cape",
                    schema = @Schema(implementation = CapeRendererType.class)
            ) @PathVariable String type,
            @Parameter(
                    description = "The size of the image (height; width derived per part)",
                    example = "768"
            ) @RequestParam(required = false, defaultValue = "768") int size
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Cape cape = this.capeService.getCapeFromTextureIdOrPlayer(query);
            byte[] bytes = this.capeService.renderCape(cape, type, size).getBytes();

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        }, Main.EXECUTOR);
    }
}