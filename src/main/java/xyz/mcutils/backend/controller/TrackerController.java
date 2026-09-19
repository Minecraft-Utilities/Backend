package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.service.TrackerStatsService;

import java.util.concurrent.TimeUnit;

/**
 * Public API of the internet server tracker. Stats are served from the cached in-memory
 * snapshot; the endpoint never touches the database.
 */
@RestController
@RequestMapping(value = "/servers/tracker")
@Tag(name = "Server Tracker Controller", description = "Statistics and tracking data of the internet server tracker.")
public class TrackerController {

    private final TrackerStatsService trackerStatsService;

    public TrackerController(TrackerStatsService trackerStatsService) {
        this.trackerStatsService = trackerStatsService;
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TrackerStatsResponse> getStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(trackerStatsService.getStats());
    }
}