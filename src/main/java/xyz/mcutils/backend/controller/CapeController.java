package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.model.domain.cape.Cape;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.service.CapeService;

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

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pagination.Page<VanillaCape>> getCapes(@Parameter(description = "The page of capes to get", example = "1") @RequestParam(required = false, defaultValue = "1") int page) {
        return ResponseEntity.ok().body(this.capeService.getPaginatedCapes(page));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VanillaCape> getCape(@Parameter(description = "The UUID of the cape") @PathVariable long id) {
        return ResponseEntity.ok().body(this.capeService.getCapeById(id));
    }

    @GetMapping(value = "/{query}/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCapeTexture(@Parameter(description = "The UUID or Username of the player or the capes's texture id", example = "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1") @PathVariable String query) {
        Cape<?> cape = VanillaCape.fromRow(this.capeService.getCapeByQuery(query));
        byte[] bytes = this.capeService.getCapeTexture(cape);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()).body(bytes);
    }

    @GetMapping(value = "/{query}/{part}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCapePart(@Parameter(description = "The UUID or Username of the player or the cape's texture id", example = "dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1") @PathVariable String query, @Parameter(description = "The part of the cape", schema = @Schema(example = "front")) @PathVariable String part, @Parameter(description = "The size of the image (height; width derived per part)", example = "768") @RequestParam(required = false, defaultValue = "768") int size) {
        Cape<?> cape = VanillaCape.fromRow(this.capeService.getCapeByQuery(query));
        byte[] bytes = this.capeService.renderCape(cape, part, size);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()).contentType(MediaType.IMAGE_PNG).body(bytes);
    }
}