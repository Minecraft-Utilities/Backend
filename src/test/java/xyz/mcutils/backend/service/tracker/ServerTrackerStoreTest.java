package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.IpLookup;
import xyz.mcutils.backend.model.domain.asn.AsnLookup;
import xyz.mcutils.backend.model.domain.geo.GeoLocation;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;
import xyz.mcutils.backend.service.MaxMindService;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link ServerTrackerStore}: buffering (no DB until flush), tracking-disabled no-op,
 * the batched multi-row statement arities (tracked_servers rows carry 24 columns, player inserts
 * 5, player updates 4, online history 5), geo enrichment on new servers and geo-failure
 * resilience. The SQL semantics themselves are verified against real Postgres in the staged
 * rollout (the project has no DB test harness).
 */
class ServerTrackerStoreTest {

    private static final UUID SERVER_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String IP = "1.2.3.4";
    private static final int PORT = 25565;

    private ServerTrackerVerifier.ServerSnapshot snapshot(int online) {
        return new ServerTrackerVerifier.ServerSnapshot(IP, PORT, online, 100, "Paper 1.21.4", 769, "Paper",
                "a test motd", null, null, false, 12, false, false, false,
                List.of(new HoneypotDetector.SampleEntry(PLAYER_UUID, "Steve")), false);
    }

    @Test
    void recordBuffersWithoutTouchingTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, mock(MaxMindService.class), true, true);

        store.record(snapshot(5));
        store.record(snapshot(6));

        verifyNoInteractions(jdbc, repo);
    }

    @Test
    void trackingDisabledMakesWritesNoOps() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, mock(MaxMindService.class), false, true);

        store.record(snapshot(5));
        assertEquals(0, store.flush());

        verifyNoInteractions(jdbc, repo);
    }

    @Test
    void flushWritesOneBatchedStatementPerTableWithStableArities() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        TrackedServerRow existing = new TrackedServerRow();
        existing.setUuid(SERVER_UUID);
        existing.setIp(IP);
        existing.setPort(PORT);
        existing.setCountry("US");
        existing.setAsn(13335L);
        when(repo.findByIpAndPort(IP, PORT)).thenReturn(Optional.of(existing));

        CapturedUpdates captured = captureUpdates(jdbc);

        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, mock(MaxMindService.class), true, true);
        store.record(snapshot(5));
        store.record(snapshot(6));
        assertEquals(2, store.flush());

        // 2 snapshots x 24 columns = one statement with 48 args: both rows batched together.
        List<Object[]> serverRows = captured.argsFor("INSERT INTO tracked_servers");
        assertEquals(1, serverRows.size(), "all server rows share one multi-row statement");
        Object[] serverRow = serverRows.get(0);
        assertEquals(48, serverRow.length, "two server rows x 24 columns");
        assertEquals(SERVER_UUID, serverRow[0]);
        assertEquals(5, serverRow[5], "first snapshot online count");
        assertEquals(6, serverRow[29], "second snapshot online count (row 2 col 5)");
        assertEquals("US", serverRow[18], "existing row keeps its country");
        assertEquals(13335L, serverRow[19], "existing row keeps its asn");

        List<Object[]> playerArgs = captured.argsFor("INSERT INTO player_history");
        assertEquals(1, playerArgs.size());
        assertEquals(10, playerArgs.get(0).length, "two player inserts x 5 columns");

        List<Object[]> updateArgs = captured.argsFor("UPDATE player_history");
        assertEquals(1, updateArgs.size());
        assertEquals(8, updateArgs.get(0).length, "two player updates x 4 columns");

        List<Object[]> historyArgs = captured.argsFor("INSERT INTO server_online_history");
        assertEquals(1, historyArgs.size());
        assertEquals(10, historyArgs.get(0).length, "two history rows x 5 columns");

        verify(repo, atLeastOnce()).findByIpAndPort(IP, PORT);
        verify(repo, never()).save(any());
    }

    @Test
    void newServerGetsGeoEnrichmentOnFirstSight() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        MaxMindService maxMind = mock(MaxMindService.class);
        when(repo.findByIpAndPort(IP, PORT)).thenReturn(Optional.empty());
        when(maxMind.lookupIp(IP)).thenReturn(new IpLookup(IP,
                new GeoLocation("United States", "US", "CA", "Los Angeles", null, null, 34.05, -118.24, null),
                new AsnLookup("AS12345", "Example Corp", "1.2.3.0/24")));

        CapturedUpdates captured = captureUpdates(jdbc);

        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, maxMind, true, true);
        store.record(snapshot(3));
        assertEquals(1, store.flush());

        Object[] serverRow = captured.argsFor("INSERT INTO tracked_servers").get(0);
        assertEquals(24, serverRow.length);
        assertEquals("US", serverRow[18], "country from MaxMind");
        assertEquals(12345L, serverRow[19], "asn number parsed from the AS-prefixed string");
        assertEquals(java.sql.Timestamp.from(Instant.EPOCH), serverRow[23], "new rows must be refreshable immediately");
        assertEquals(0, serverRow[22], "new rows start alive (consecutive_offline = 0)");
    }

    @Test
    void geoFailureLeavesColumnsNullAndNeverFailsTheFlush() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        MaxMindService maxMind = mock(MaxMindService.class);
        when(repo.findByIpAndPort(IP, PORT)).thenReturn(Optional.empty());
        when(maxMind.lookupIp(IP)).thenThrow(new NotFoundException("No data found for IP address: " + IP));

        CapturedUpdates captured = captureUpdates(jdbc);

        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, maxMind, true, true);
        store.record(snapshot(3));
        assertEquals(1, store.flush(), "geo failure must not fail the flush");

        Object[] serverRow = captured.argsFor("INSERT INTO tracked_servers").get(0);
        assertNull(serverRow[18], "country stays NULL when geo lookup fails");
        assertNull(serverRow[19], "asn stays NULL when geo lookup fails");
    }

    @Test
    void databaseFailureDuringFlushIsContained() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ServerTrackerRepository repo = mock(ServerTrackerRepository.class);
        when(repo.findByIpAndPort(IP, PORT)).thenReturn(Optional.empty());
        when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenThrow(new RuntimeException("connection lost"));

        ServerTrackerStore store = new ServerTrackerStore(jdbc, repo, mock(MaxMindService.class), true, false);
        store.record(snapshot(3));
        assertEquals(0, store.flush(), "failed flush reports zero written and does not throw");
    }

    /** Captures every (sql, args) pair the store sends, replaying the setters via a proxy. */
    private static CapturedUpdates captureUpdates(JdbcTemplate jdbc) throws Exception {
        CapturedUpdates captured = new CapturedUpdates();
        when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenAnswer(invocation -> {
            captured.add(invocation.getArgument(0), invocation.getArgument(1));
            return 1;
        });
        return captured;
    }

    private static final class CapturedUpdates {
        private final List<String> sqls = new ArrayList<>();
        private final List<Object[]> argsList = new ArrayList<>();

        void add(String sql, PreparedStatementSetter setter) throws Exception {
            sqls.add(sql);
            int columns = countColumns(sql);
            Object[] values = new Object[columns];
            PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                    CapturedUpdates.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setObject") && args.length >= 2) {
                            values[((Number) args[0]).intValue() - 1] = args[1];
                        }
                        return null;
                    });
            setter.setValues(ps);
            argsList.add(values);
        }

        List<Object[]> argsFor(String sqlFragment) {
            List<Object[]> matches = new ArrayList<>();
            for (int i = 0; i < sqls.size(); i++) {
                if (sqls.get(i).contains(sqlFragment)) {
                    matches.add(argsList.get(i));
                }
            }
            return matches;
        }

        /** Total placeholder count of the statement (rows x columns). */
        private static int countColumns(String sql) {
            int count = 0;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    count++;
                }
            }
            return count;
        }
    }
}