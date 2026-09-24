package xyz.mcutils.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.model.dto.request.TrackerServerFilterRequest;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerSearchResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerServerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.service.TrackerQueryService;
import xyz.mcutils.backend.service.TrackerStatsService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrackerControllerTest {

    private static final UUID SERVER_UUID = UUID.fromString("0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf10");
    private static final UUID PLAYER_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final Instant LAST_UPDATED = Instant.parse("2026-09-23T10:15:30Z");

    private TrackerQueryService trackerQueryService;
    private TrackerStatsService trackerStatsService;
    private TrackerController controller;

    @BeforeEach
    void setUp() {
        trackerStatsService = mock(TrackerStatsService.class);
        trackerQueryService = mock(TrackerQueryService.class);
        controller = new TrackerController(trackerStatsService, trackerQueryService);
    }

    @Test
    void statsEndpointReturnsSnapshotWithPublicCache() {
        TrackerStatsResponse stats = new TrackerStatsResponse(12, 34, 5, Map.of("US", 8L), Map.of("paper", 7L), Map.of("769", 6L));
        when(trackerStatsService.getStats()).thenReturn(stats);

        ResponseEntity<TrackerStatsResponse> response = controller.getStats();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(stats, response.getBody());
        assertPublicCache(response);
        verifyNoInteractions(trackerQueryService);
    }

    @Test
    void getServersForwardsFiltersAndReturnsPublicCache() {
        TrackedServerSummaryResponse summary = summary();
        Pagination.Page<TrackedServerSummaryResponse> page = new Pagination.Page<>(List.of(summary), 51, 50, 2);
        TrackerServerFilterRequest request = new TrackerServerFilterRequest(
                2, "198.51", null, null, 769, 5, 100, "onlineCount", "desc"
        );
        when(trackerQueryService.getServers(request)).thenReturn(page);

        ResponseEntity<Pagination.Page<TrackedServerSummaryResponse>> response = controller.getServers(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(page, response.getBody());
        assertPublicCache(response);
        verify(trackerQueryService).getServers(request);
    }

    @Test
    void getServerDetailReturnsPublicDetailAndCache() {
        TrackedServerDetailResponse detail = detail();
        when(trackerQueryService.getServerDetail(SERVER_UUID.toString())).thenReturn(detail);

        ResponseEntity<TrackedServerDetailResponse> response = controller.getServerDetail(SERVER_UUID.toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(detail, response.getBody());
        assertPublicCache(response);
        verify(trackerQueryService).getServerDetail(SERVER_UUID.toString());
    }

    @Test
    void searchPlayersForwardsQueryAndDisablesCaching() {
        List<TrackedPlayerSearchResponse> matches =
                List.of(new TrackedPlayerSearchResponse(PLAYER_UUID, "Notch", 42));
        when(trackerQueryService.searchPlayers("Notch")).thenReturn(matches);

        ResponseEntity<List<TrackedPlayerSearchResponse>> response = controller.searchPlayers("Notch");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(matches, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(trackerQueryService).searchPlayers("Notch");
    }

    @Test
    void getPlayersForwardsUuidAndPageAndReturnsPublicCache() {
        TrackedPlayerResponse player = playerResponse();
        when(trackerQueryService.getPlayers(PLAYER_UUID.toString(), 1)).thenReturn(player);

        ResponseEntity<TrackedPlayerResponse> response = controller.getPlayers(PLAYER_UUID.toString(), 1);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(player, response.getBody());
        assertPublicCache(response);
        verify(trackerQueryService).getPlayers(PLAYER_UUID.toString(), 1);
    }

    private static void assertPublicCache(ResponseEntity<?> response) {
        String cacheControl = response.getHeaders().getCacheControl();
        assertTrue(cacheControl != null && cacheControl.contains("max-age=60"), "response must be publicly cached for 60 seconds");
        assertTrue(cacheControl.contains("public"), "response cache must be public");
    }

    private static TrackedServerSummaryResponse summary() {
        return new TrackedServerSummaryResponse(
                SERVER_UUID,
                "198.51.100.10",
                25565,
                "Paper 1.21.4",
                769,
                "Paper",
                18,
                100,
                "US",
                true,
                LAST_UPDATED
        );
    }

    private static TrackedServerDetailResponse detail() {
        return new TrackedServerDetailResponse(
                SERVER_UUID,
                "198.51.100.10",
                25565,
                "Paper 1.21.4",
                769,
                "Paper",
                18,
                100,
                "US",
                true,
                LAST_UPDATED,
                Instant.parse("2026-08-01T03:00:00Z"),
                LAST_UPDATED,
                "Public server",
                45,
                false,
                false,
                false,
                false,
                64500L
        );
    }

    private static TrackedPlayerResponse playerResponse() {
        TrackedPlayerServerResponse sighting = new TrackedPlayerServerResponse(
                "ImFascinated",
                Instant.parse("2026-08-01T03:00:00Z"),
                LAST_UPDATED,
                4,
                summary()
        );
        return new TrackedPlayerResponse(PLAYER_UUID, new Pagination.Page<>(List.of(sighting), 1, 50, 1));
    }
}
