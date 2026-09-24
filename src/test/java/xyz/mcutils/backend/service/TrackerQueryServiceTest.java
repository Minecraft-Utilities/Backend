package xyz.mcutils.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.request.TrackerServerFilterRequest;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerSearchResponse;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;
import java.time.Instant;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrackerQueryServiceTest {

    private static final UUID SERVER_UUID = UUID.fromString("0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf10");
    private static final UUID PLAYER_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final Instant LAST_UPDATED = Instant.parse("2026-09-23T10:15:30Z");
    private static final int PER_PAGE = 50;

    private ServerTrackerRepository serverRepository;
    private PlayerHistoryRepository playerRepository;
    private TrackerQueryService service;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerTrackerRepository.class);
        playerRepository = mock(PlayerHistoryRepository.class);
        service = new TrackerQueryService(serverRepository, playerRepository, true);
    }

    @Test
    void getServersMapsSummaryAndUsesFilteredPagination() {
        when(serverRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(serverRow()), PageRequest.of(0, PER_PAGE), 1));

        Pagination.Page<TrackedServerSummaryResponse> result = service.getServers(1);

        assertEquals(1, result.getItems().size());
        TrackedServerSummaryResponse summary = result.getItems().get(0);
        assertEquals(SERVER_UUID, summary.uuid());
        assertEquals("198.51.100.10", summary.ip());
        assertEquals(25565, summary.port());
        assertEquals("Paper 1.21.4", summary.version());
        assertEquals(Integer.valueOf(769), summary.protocol());
        assertEquals("Paper", summary.platform());
        assertEquals(18, summary.onlineCount());
        assertEquals(100, summary.maxPlayers());
        assertEquals("US", summary.country());
        assertTrue(summary.online());
        assertEquals(LAST_UPDATED, summary.lastUpdated());
        assertEquals(1L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertEquals(1, result.getTotalPages());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(serverRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(PER_PAGE, pageable.getValue().getPageSize());
        assertEquals("lastUpdated", pageable.getValue().getSort().getOrderFor("lastUpdated").getProperty());
    }

    @Test
    void getServersAppliesRequestedSort() {
        when(serverRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, PER_PAGE), 0));
        TrackerServerFilterRequest request = new TrackerServerFilterRequest(
                1, null, null, null, null, null, null, "onlineCount", "asc"
        );

        service.getServers(request);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(serverRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(Sort.Direction.ASC, pageable.getValue().getSort().getOrderFor("onlineCount").getDirection());
    }

    @Test
    void getServersReturnsEmptyFilteredPage() {
        when(serverRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, PER_PAGE), 0));

        Pagination.Page<TrackedServerSummaryResponse> result = service.getServers(1);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertEquals(0, result.getTotalPages());
    }

    @Test
    void getServersRejectsInvalidPagesFiltersAndSorts() {
        assertThrows(BadRequestException.class, () -> service.getServers(0));
        assertThrows(BadRequestException.class, () -> service.getServers(
                new TrackerServerFilterRequest(1, null, null, null, null, 20, 10, null, null)
        ));
        assertThrows(BadRequestException.class, () -> service.getServers(
                new TrackerServerFilterRequest(1, null, null, null, null, null, null, "unknown", null)
        ));
        assertThrows(BadRequestException.class, () -> service.getServers(
                new TrackerServerFilterRequest(1, null, null, null, null, null, null, null, "sideways")
        ));
        verifyNoInteractions(serverRepository);
    }

    @Test
    void getServersIsEmptyWithoutRepositoryReadsWhenDisabled() {
        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, false);

        Pagination.Page<TrackedServerSummaryResponse> result = disabled.getServers(1);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertThrows(BadRequestException.class, () -> disabled.getServers(2));
        verifyNoInteractions(serverRepository);
    }

    @Test
    void getServerDetailMapsPublicFields() {
        TrackedServerRow row = serverRow();
        row.setConsecutiveOffline(2);
        row.setFirstSeen(Instant.parse("2026-08-01T03:00:00Z"));
        row.setLastRefreshed(Instant.parse("2026-09-23T10:20:00Z"));
        row.setMotd("Public server");
        row.setLatencyMs(45);
        row.setModded(true);
        row.setPreventsChatReports(true);
        row.setEnforcesSecureChat(true);
        row.setPreviewsChat(true);
        row.setAsn(64500L);
        when(serverRepository.findByUuidAndHoneypotFalse(SERVER_UUID)).thenReturn(Optional.of(row));

        TrackedServerDetailResponse detail = service.getServerDetail(SERVER_UUID.toString());

        assertEquals(SERVER_UUID, detail.uuid());
        assertEquals(LAST_UPDATED, detail.lastUpdated());
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), detail.firstSeen());
        assertEquals(Instant.parse("2026-09-23T10:20:00Z"), detail.lastCheckedAt());
        assertFalse(detail.online());
        assertEquals("Public server", detail.motd());
        assertEquals(Integer.valueOf(45), detail.latencyMs());
        assertTrue(detail.modded());
        assertTrue(detail.preventsChatReports());
        assertTrue(detail.enforcesSecureChat());
        assertTrue(detail.previewsChat());
        assertEquals(Long.valueOf(64500L), detail.asn());
    }

    @Test
    void getServerDetailRejectsInvalidUnknownAndDisabledLookups() {
        UUID unknown = UUID.fromString("0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf11");
        when(serverRepository.findByUuidAndHoneypotFalse(unknown)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.getServerDetail("invalid"));
        assertThrows(NotFoundException.class, () -> service.getServerDetail(unknown.toString()));

        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, false);
        assertThrows(NotFoundException.class, () -> disabled.getServerDetail(SERVER_UUID.toString()));
    }

    @Test
    void searchPlayersReturnsTrackedMatchesWithBoundedPage() {
        PlayerHistoryRepository.TrackedPlayerSearchProjection projection =
                mock(PlayerHistoryRepository.TrackedPlayerSearchProjection.class);
        when(projection.getPlayerUuid()).thenReturn(PLAYER_UUID);
        when(projection.getUsername()).thenReturn("Notch");
        when(projection.getSkinId()).thenReturn(42L);
        when(playerRepository.searchPublicPlayers(eq("Notch"), any())).thenReturn(List.of(projection));

        List<TrackedPlayerSearchResponse> result = service.searchPlayers(" Notch ");

        assertEquals(1, result.size());
        assertEquals(PLAYER_UUID, result.get(0).playerUuid());
        assertEquals("Notch", result.get(0).username());
        assertEquals(42L, result.get(0).skinId());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(playerRepository).searchPublicPlayers(eq("Notch"), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    void searchPlayersSkipsRepositoryForBlankOrDisabledSearch() {
        assertTrue(service.searchPlayers("   ").isEmpty());
        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, false);
        assertTrue(disabled.searchPlayers("Notch").isEmpty());

        verifyNoInteractions(playerRepository);
    }

    @Test
    void getPlayersMapsSightingsAndUsesFiftyItemPage() {
        PlayerHistoryRepository.PlayerSightingProjection first = projection(SERVER_UUID, LAST_UPDATED);
        PlayerHistoryRepository.PlayerSightingProjection second = projection(SERVER_UUID, LAST_UPDATED.minusSeconds(60));
        when(playerRepository.countPublicSightingsByPlayerUuid(PLAYER_UUID)).thenReturn(2L);
        when(playerRepository.findPublicSightingsByPlayerUuid(eq(PLAYER_UUID), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, PER_PAGE), 2));

        TrackedPlayerResponse result = service.getPlayers(PLAYER_UUID.toString(), 1);

        assertEquals(PLAYER_UUID, result.playerUuid());
        assertEquals(2, result.servers().getItems().size());
        assertEquals(2L, result.servers().getTotalItems());
        assertEquals(PER_PAGE, result.servers().getItemsPerPage());
        assertEquals(1, result.servers().getTotalPages());
        assertEquals("ImFascinated", result.servers().getItems().get(0).username());
        assertEquals(SERVER_UUID, result.servers().getItems().get(0).server().uuid());
        assertTrue(result.servers().getItems().get(0).server().online());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(playerRepository).findPublicSightingsByPlayerUuid(eq(PLAYER_UUID), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(PER_PAGE, pageable.getValue().getPageSize());
    }

    @Test
    void getPlayersRejectsInvalidPageBeforeCountQuery() {
        assertThrows(BadRequestException.class, () -> service.getPlayers(PLAYER_UUID.toString(), 0));

        verifyNoInteractions(playerRepository);
    }

    @Test
    void getPlayersRejectsUnknownAndOutOfRangePages() {
        when(playerRepository.countPublicSightingsByPlayerUuid(PLAYER_UUID)).thenReturn(3L);

        assertThrows(BadRequestException.class, () -> service.getPlayers(PLAYER_UUID.toString(), 2));

        UUID unknown = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf6");
        when(playerRepository.countPublicSightingsByPlayerUuid(unknown)).thenReturn(0L);
        assertThrows(NotFoundException.class, () -> service.getPlayers(unknown.toString(), 1));
    }

    @Test
    void getPlayersReturnsNotFoundWithoutRepositoryReadsWhenDisabled() {
        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, false);

        assertThrows(NotFoundException.class, () -> disabled.getPlayers(PLAYER_UUID.toString(), 1));

        verifyNoInteractions(playerRepository);
    }


    private static TrackedServerRow serverRow() {
        TrackedServerRow row = new TrackedServerRow();
        row.setUuid(SERVER_UUID);
        row.setIp("198.51.100.10");
        row.setPort(25565);
        row.setVersion("Paper 1.21.4");
        row.setProtocol(769);
        row.setPlatform("Paper");
        row.setOnlineCount(18);
        row.setMaxPlayers(100);
        row.setCountry("US");
        row.setConsecutiveOffline(0);
        row.setLastUpdated(LAST_UPDATED);
        return row;
    }

    private static PlayerHistoryRepository.PlayerSightingProjection projection(UUID serverUuid, Instant lastSeen) {
        PlayerHistoryRepository.PlayerSightingProjection projection = mock(PlayerHistoryRepository.PlayerSightingProjection.class);
        when(projection.getUsername()).thenReturn("ImFascinated");
        when(projection.getFirstSeen()).thenReturn(Instant.parse("2026-08-01T03:00:00Z"));
        when(projection.getLastSeen()).thenReturn(lastSeen);
        when(projection.getTimesSeen()).thenReturn(4);
        when(projection.getServerUuid()).thenReturn(serverUuid);
        when(projection.getIp()).thenReturn("198.51.100.10");
        when(projection.getPort()).thenReturn(25565);
        when(projection.getVersion()).thenReturn("Paper 1.21.4");
        when(projection.getProtocol()).thenReturn(769);
        when(projection.getPlatform()).thenReturn("Paper");
        when(projection.getOnlineCount()).thenReturn(18);
        when(projection.getMaxPlayers()).thenReturn(100);
        when(projection.getCountry()).thenReturn("US");
        when(projection.getOnline()).thenReturn(true);
        when(projection.getLastUpdated()).thenReturn(lastSeen);
        return projection;
    }
}
