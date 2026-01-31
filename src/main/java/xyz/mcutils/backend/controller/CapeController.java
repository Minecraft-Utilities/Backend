package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.service.CapeService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/cape")
@Tag(name = "Cape Controller", description = "The Cape Controller is used to get cape images.")
public class CapeController {
    private final CapeService capeService;

    @Autowired
    public CapeController(CapeService capeService) {
        this.capeService = capeService;
    }

    @ResponseBody
    @GetMapping(value = "/texture/{id}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getPlayer(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(this.capeService.getCapeImage(Cape.fromId(id)));
    }
}
