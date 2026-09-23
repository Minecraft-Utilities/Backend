package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.service.TrackerQueryService;
import xyz.mcutils.backend.service.TrackerStatsService;

import java.util.concurrent.TimeUnit;

/**
 * Public API of the internet server tracker. Statistics are served from the cached in-memory
 * snapshot; collection, detail, and tracked-player reads are served from the read model and
 * never touch the database directly.
 */
@RestController
@RequestMapping(value = "/tracker")
@Tag(name = "Server Tracker Controller", description = "Statistics and tracking data of the internet server tracker.")
public class TrackerController {

    private final TrackerStatsService trackerStatsService;
    private final TrackerQueryService trackerQueryService;

    public TrackerController(TrackerStatsService trackerStatsService, TrackerQueryService trackerQueryService) {
        this.trackerStatsService = trackerStatsService;
        this.trackerQueryService = trackerQueryService;
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cached tracker statistics")
    public ResponseEntity<TrackerStatsResponse> getStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(trackerStatsService.getStats());
    }

    @GetMapping(value = "/servers", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Page of tracked servers", description = "Fixed 50-item page of tracked servers.")
    public ResponseEntity<Pagination.Page<TrackedServerSummaryResponse>> getServers(
            @Parameter(description = "One-based page number", example = "1") @RequestParam(required = false, defaultValue = "1") int page) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(trackerQueryService.getServers(page));
    }

    @GetMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tracked server detail", description = "Full public detail for one tracker server UUID.")
    public ResponseEntity<TrackedServerDetailResponse> getServerDetail(
            @Parameter(description = "Canonical tracker server UUID", example = "0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf10") @PathVariable String uuid) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(trackerQueryService.getServerDetail(uuid));
    }

    @GetMapping(value = "/players/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tracked player sightings", description = "Fixed 50-item page of sightings for a player UUID.")
    public ResponseEntity<TrackedPlayerResponse> getPlayers(
            @Parameter(description = "Canonical player UUID", example = "069a79f4-44e9-4726-a5be-fca90e38aaf5") @PathVariable String uuid,
            @Parameter(description = "One-based page number", example = "1") @RequestParam(required = false, defaultValue = "1") int page) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(trackerQueryService.getPlayers(uuid, page));
    }
}
