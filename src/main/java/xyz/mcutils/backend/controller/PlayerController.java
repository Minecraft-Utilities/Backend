package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.cache.CachedPlayerName;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.UUIDSubmission;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.PlayerUpdateService;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/player")
@Tag(name = "Player Controller", description = "The Player Controller is used to get information about a player.")
public class PlayerController {

    private final PlayerService playerService;
    private final SkinService skinService;
    private final PlayerUpdateService playerUpdateService;

    @Autowired
    public PlayerController(PlayerService playerManagerService, SkinService skinService, PlayerUpdateService playerUpdateService) {
        this.playerService = playerManagerService;
        this.skinService = skinService;
        this.playerUpdateService = playerUpdateService;
    }

    @ResponseBody
    @PostMapping(value = "/submit-uuids")
    public ResponseEntity<?> submitUUIDs(@RequestBody UUIDSubmission submission) {
        return ResponseEntity.ok(playerUpdateService.submitUUIDs(submission));
    }

    @ResponseBody
    @GetMapping(value = "/top-contributors")
    public ResponseEntity<?> getTopContributors() {
        return ResponseEntity.ok(playerService.getTopContributors());
    }

    @ResponseBody
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPlayer(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id) {
        Player player = playerService.getPlayer(id, true);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(player);
    }

    @ResponseBody
    @GetMapping(value = "/uuid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CachedPlayerName> getPlayerUuid(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id) {
        CachedPlayerName player = playerService.usernameToUuid(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(player);
    }

    @GetMapping(value = "/{id}/skin/{part}.{extension}")
    public ResponseEntity<?> getPlayerSkinPart(
            @Parameter(description = "The UUID or Username of the player", example = "ImFascinated") @PathVariable String id,
            @Parameter(description = "The part of the skin", example = "head") @PathVariable String part,
            @Parameter(description = "The file extension of the image", example = "png") @PathVariable String extension,
            @Parameter(description = "The size of the image", example = "256") @RequestParam(required = false, defaultValue = "256") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "false") @RequestParam(required = false, defaultValue = "false") boolean overlays,
            @Parameter(description = "Whether to download the image") @RequestParam(required = false, defaultValue = "false") boolean download) {
        Player player = playerService.getPlayer(id, true);
        String dispositionHeader = download ? "attachment; filename=%s.png" : "inline; filename=%s.png";

        // Return the part image
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionHeader.formatted(player.getUsername()))
                .body(skinService.getSkinPart(player, part, overlays, size).getBytes());
    }
}
