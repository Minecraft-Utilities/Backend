package xyz.mcutils.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.model.dto.response.TrackerStatsResponse;
import xyz.mcutils.backend.repository.postgres.PlayerHistoryRepository;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link TrackerStatsService}: the cached in-memory snapshot served to the stats
 * endpoint, its refresh semantics (tracking-disabled no-op, no DB access on reads).
 */
class TrackerStatsServiceTest {

    private ServerTrackerRepository serverRepository;
    private PlayerHistoryRepository playerRepository;
    private TrackerStatsService service;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerTrackerRepository.class);
        playerRepository = mock(PlayerHistoryRepository.class);
        service = new TrackerStatsService(serverRepository, playerRepository, true, 300);
    }

    @Test
    void refreshLoadsCountersAndBreakdowns() {
        when(serverRepository.countTrackedServers()).thenReturn(42L);
        when(playerRepository.countDistinctPlayers()).thenReturn(7L);
        when(serverRepository.sumOnlinePlayers()).thenReturn(3L);
        when(serverRepository.topCountries(any())).thenReturn(List.of(breakdown("US", 10L), breakdown("DE", 4L)));
        when(serverRepository.topPlatforms(any())).thenReturn(List.of(breakdown("paper", 9L)));
        when(serverRepository.topProtocols(any())).thenReturn(List.of(breakdown("769", 8L)));

        service.refresh();

        TrackerStatsResponse stats = service.getStats();
        assertEquals(42L, stats.trackedServers());
        assertEquals(7L, stats.trackedPlayers());
        assertEquals(3L, stats.onlinePlayers());
        assertEquals(Map.of("US", 10L, "DE", 4L), stats.geo());
        assertEquals(Map.of("paper", 9L), stats.platform());
        assertEquals(Map.of("769", 8L), stats.protocol());
    }

    @Test
    void getStatsServesSnapshotWithoutRequeryingRepositories() {
        when(serverRepository.countTrackedServers()).thenReturn(5L);
        when(playerRepository.countDistinctPlayers()).thenReturn(2L);
        when(serverRepository.sumOnlinePlayers()).thenReturn(1L);
        when(serverRepository.topCountries(any())).thenReturn(List.of());
        when(serverRepository.topPlatforms(any())).thenReturn(List.of());
        when(serverRepository.topProtocols(any())).thenReturn(List.of());
        service.refresh();

        TrackerStatsResponse stats = service.getStats();
        assertEquals(5L, stats.trackedServers());

        verify(serverRepository).countTrackedServers();
        verify(serverRepository).sumOnlinePlayers();
        verify(serverRepository).topCountries(any());
        verify(serverRepository).topPlatforms(any());
        verify(serverRepository).topProtocols(any());
        verify(playerRepository).countDistinctPlayers();
        verifyNoMoreInteractions(serverRepository, playerRepository);
    }

    @Test
    void refreshIsNoOpWhenTrackingDisabled() {
        TrackerStatsService disabled = new TrackerStatsService(serverRepository, playerRepository, false, 300);

        disabled.refresh();

        TrackerStatsResponse stats = disabled.getStats();
        assertEquals(0L, stats.trackedServers());
        assertEquals(0L, stats.trackedPlayers());
        assertEquals(0L, stats.onlinePlayers());
        assertEquals(Map.of(), stats.geo());
        verifyNoInteractions(serverRepository, playerRepository);
    }

    @Test
    void refreshFailureKeepsLastSnapshot() {
        when(serverRepository.countTrackedServers()).thenReturn(5L);
        when(playerRepository.countDistinctPlayers()).thenReturn(2L);
        when(serverRepository.sumOnlinePlayers()).thenReturn(1L);
        when(serverRepository.topCountries(any())).thenReturn(List.of());
        when(serverRepository.topPlatforms(any())).thenReturn(List.of());
        when(serverRepository.topProtocols(any())).thenReturn(List.of());
        service.refresh();

        when(serverRepository.countTrackedServers()).thenThrow(new RuntimeException("db down"));
        service.refresh();

        assertEquals(5L, service.getStats().trackedServers(), "failed refresh must keep the last good snapshot");
    }

    private static ServerTrackerRepository.Breakdown breakdown(String key, long total) {
        return new ServerTrackerRepository.Breakdown() {
            @Override
            public String getGroupKey() {
                return key;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}