package xyz.mcutils.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import xyz.mcutils.backend.common.Pagination;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.response.TrackedPlayerResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerDetailResponse;
import xyz.mcutils.backend.model.dto.response.TrackedServerSummaryResponse;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private TrackerStatsService statsService;
    private TrackerQueryService service;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerTrackerRepository.class);
        playerRepository = mock(PlayerHistoryRepository.class);
        statsService = mock(TrackerStatsService.class);
        service = new TrackerQueryService(serverRepository, playerRepository, statsService, true);
    }

    @Test
    void getServersMapsSummaryAndUsesFiftyItemPage() {
        when(statsService.getStats()).thenReturn(stats(42));
        when(serverRepository.findByHoneypotFalseOrderByLastUpdatedDescUuidAsc(any()))
                .thenReturn(new SliceImpl<>(List.of(serverRow()), PageRequest.of(0, PER_PAGE), false));

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
        assertEquals(42L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertEquals(1, result.getTotalPages());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(serverRepository).findByHoneypotFalseOrderByLastUpdatedDescUuidAsc(pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(PER_PAGE, pageable.getValue().getPageSize());
    }

    @Test
    void getServersReturnsEmptyPageWithoutRepositoryQuery() {
        when(statsService.getStats()).thenReturn(stats(0));

        Pagination.Page<TrackedServerSummaryResponse> result = service.getServers(1);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertEquals(0, result.getTotalPages());
        verify(serverRepository, never()).findByHoneypotFalseOrderByLastUpdatedDescUuidAsc(any());
    }

    @Test
    void getServersRejectsInvalidAndOutOfRangePages() {
        when(statsService.getStats()).thenReturn(stats(10));

        assertThrows(BadRequestException.class, () -> service.getServers(0));
        assertThrows(BadRequestException.class, () -> service.getServers(2));
        verify(serverRepository, never()).findByHoneypotFalseOrderByLastUpdatedDescUuidAsc(any());
    }

    @Test
    void getServersIsEmptyWithoutStatsOrRepositoryReadsWhenDisabled() {
        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, statsService, false);

        Pagination.Page<TrackedServerSummaryResponse> result = disabled.getServers(1);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalItems());
        assertEquals(PER_PAGE, result.getItemsPerPage());
        assertThrows(BadRequestException.class, () -> disabled.getServers(2));
        verifyNoInteractions(statsService, serverRepository);
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
        assertEquals(2, detail.consecutiveOffline());
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

        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, statsService, false);
        assertThrows(NotFoundException.class, () -> disabled.getServerDetail(SERVER_UUID.toString()));
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
        TrackerQueryService disabled = new TrackerQueryService(serverRepository, playerRepository, statsService, false);

        assertThrows(NotFoundException.class, () -> disabled.getPlayers(PLAYER_UUID.toString(), 1));

        verifyNoInteractions(playerRepository);
    }

    private static TrackerStatsResponse stats(long trackedServers) {
        return new TrackerStatsResponse(trackedServers, 1, 1, Map.of("US", trackedServers), Map.of("paper", trackedServers), Map.of("769", trackedServers));
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
