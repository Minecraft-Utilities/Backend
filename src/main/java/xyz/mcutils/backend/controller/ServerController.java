package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.model.cache.CachedMinecraftServer;
import xyz.mcutils.backend.model.response.ServerBlockedResponse;
import xyz.mcutils.backend.model.server.Platform;
import xyz.mcutils.backend.model.serverregistry.ServerRegistryEntry;
import xyz.mcutils.backend.service.MojangService;
import xyz.mcutils.backend.service.ServerRegistryService;
import xyz.mcutils.backend.service.ServerService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(value = "/servers")
@Tag(name = "Server Controller", description = "The Server Controller is used to get information about a server.")
public class ServerController {

    private final ServerService serverService;
    private final ServerRegistryService serverRegistryService;
    private final MojangService mojangService;

    @Autowired
    public ServerController(ServerService serverService, ServerRegistryService serverRegistryService, MojangService mojangService) {
        this.serverService = serverService;
        this.serverRegistryService = serverRegistryService;
        this.mojangService = mojangService;
    }

    @ResponseBody
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ServerRegistryEntry>> getServers(
            @Parameter(
                    description = "The query to search for",
                    example = "WildNetwork"
            ) @RequestParam String query
    ) {
        List<ServerRegistryEntry> entries = this.serverRegistryService.searchEntries(query);
        return ResponseEntity.ok()
                .body(entries);
    }

    @ResponseBody
    @GetMapping(value = "/{platform}/{hostname}")
    public CompletableFuture<ResponseEntity<CachedMinecraftServer>> getServer(
            @PathVariable String platform,
            @PathVariable String hostname
    ) {
        return CompletableFuture.supplyAsync(() -> serverService.getServer(platform, hostname), Main.EXECUTOR)
                .thenApply(ResponseEntity::ok);
    }

    @ResponseBody
    @GetMapping(value = "/{hostname}/icon.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getServerIcon(
            @Parameter(
                    description = "The hostname and port of the server",
                    example = "aetheria.cc"
            ) @PathVariable String hostname
    ) {
        return CompletableFuture.supplyAsync(() -> serverService.getServerFavicon(hostname), Main.EXECUTOR)
                .thenApply(favicon -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(favicon));
    }

    @ResponseBody
    @GetMapping(value = "/{platform}/{hostname}/preview.png", produces = MediaType.IMAGE_PNG_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> getServerPreview(
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
        return CompletableFuture.supplyAsync(() -> {
            CachedMinecraftServer server = serverService.getServer(platform, hostname);
            return serverService.getServerPreview(server, platform, size);
        }, Main.EXECUTOR).thenApply(preview -> ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(preview));
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
