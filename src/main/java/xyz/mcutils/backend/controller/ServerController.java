package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.model.cache.CachedMinecraftServer;
import xyz.mcutils.backend.model.response.ServerBlockedResponse;
import xyz.mcutils.backend.model.server.Platform;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.ServerService;

@RestController
@RequestMapping(value = "/server/")
@Tag(name = "Server Controller", description = "The Server Controller is used to get information about a server.")
public class ServerController {

    private final ServerService serverService;
    private final MojangService mojangService;

    @Autowired
    public ServerController(ServerService serverService, MojangService mojangService) {
        this.serverService = serverService;
        this.mojangService = mojangService;
    }

    @ResponseBody
    @GetMapping(value = "/{platform}/{hostname}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CachedMinecraftServer> getServer(
            @Parameter(
                    description = "The platform of the server",
                    schema = @Schema(implementation = Platform.class)
            ) @PathVariable String platform,
            @Parameter(
                    description = "The hostname and port of the server",
                    example = "aetheria.cc"
            ) @PathVariable String hostname
    ) {
        return ResponseEntity.ok()
                .body(this.serverService.getServer(platform, hostname));
    }

    @ResponseBody
    @GetMapping(value = "/{hostname}/icon.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getServerIcon(
            @Parameter(
                    description = "The hostname and port of the server",
                    example = "aetheria.cc"
            ) @PathVariable String hostname
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(this.serverService.getServerFavicon(hostname));
    }

    @ResponseBody
    @GetMapping(value = "/{platform}/{hostname}/preview.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getServerPreview(
            @Parameter(
                    description = "The platform of the server",
                    schema = @Schema(implementation = Platform.class)
            ) @PathVariable String platform,
            @Parameter(
                    description = "The hostname and port of the server",
                    example = "aetheria.cc"
            ) @PathVariable String hostname,
            @Parameter(
                    description = "The size of the image",
                    example = "768"
            ) @RequestParam(required = false, defaultValue = "768") int size
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(this.serverService.getServerPreview(this.serverService.getServer(platform, hostname), platform, size));
    }

    @ResponseBody
    @GetMapping(value = "/blocked/{hostname}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServerBlockedResponse> getServerBlockedStatus(
            @Parameter(
                    description = "The hostname of the server",
                    example = "aetheria.cc"
            ) @PathVariable String hostname
    ) {
        return ResponseEntity.ok()
                .body(new ServerBlockedResponse(this.mojangService.isServerBlocked(hostname)));
    }
}
