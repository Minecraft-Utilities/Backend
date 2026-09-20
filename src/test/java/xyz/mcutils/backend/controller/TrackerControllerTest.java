package xyz.mcutils.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.service.TrackerStatsService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link TrackerController}: response shape, caching header, disabled-tracking state.
 */
class TrackerControllerTest {

    @Test
    void statsEndpointReturnsSnapshotWithCacheHeader() {
        TrackerStatsService service = mock(TrackerStatsService.class);
        when(service.getStats()).thenReturn(new TrackerStatsResponse(
                12L, 34L, 5L,
                Map.of("US", 8L),
                Map.of("paper", 7L),
                Map.of("769", 6L)
        ));
        TrackerController controller = new TrackerController(service);

        ResponseEntity<TrackerStatsResponse> response = controller.getStats();

        assertEquals(200, response.getStatusCode().value());
        TrackerStatsResponse body = response.getBody();
        assertEquals(12L, body.trackedServers());
        assertEquals(34L, body.trackedPlayers());
        assertEquals(5L, body.verifiedOnlinePlayers());
        assertEquals(Map.of("US", 8L), body.geo());
        assertEquals(Map.of("paper", 7L), body.platform());
        assertEquals(Map.of("769", 6L), body.protocol());
        String cacheControl = response.getHeaders().getCacheControl();
        assertTrue(cacheControl != null && cacheControl.contains("max-age=60"), "stats must be publicly cached for 60s, got: " + cacheControl);
    }

    @Test
    void statsEndpointReturnsEmptyStateWhenTrackingDisabled() {
        TrackerStatsService service = mock(TrackerStatsService.class);
        when(service.getStats()).thenReturn(new TrackerStatsResponse(0, 0, 0, Map.of(), Map.of(), Map.of()));
        TrackerController controller = new TrackerController(service);

        ResponseEntity<TrackerStatsResponse> response = controller.getStats();

        TrackerStatsResponse body = response.getBody();
        assertEquals(0L, body.trackedServers());
        assertEquals(0L, body.verifiedOnlinePlayers());
        assertEquals(Map.of(), body.geo());
        assertEquals(Map.of(), body.platform());
        assertEquals(Map.of(), body.protocol());
    }
}