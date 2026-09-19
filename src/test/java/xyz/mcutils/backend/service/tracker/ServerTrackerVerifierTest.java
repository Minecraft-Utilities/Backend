package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.service.PlayerService;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link ServerTrackerVerifier} against a real Java status protocol server
 * on 127.0.0.1: real handshake, real token parsing, real port walk, real honeypot filtering.
 */
class ServerTrackerVerifierTest {

    private static final String IP = "127.0.0.1";
    private static final UUID STEVE = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48");
    private static final UUID ALEX = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final UUID JEB = UUID.fromString("b876ec32-e396-476b-a115-8438d83c67d4");

    private record SinkRecord(String ip, int port, int sampleCount, boolean honeypot) {}

    /** Identity verification is a no-op for these protocol-level integration tests. */
    private static final PlayerSampleVerifier PASS_THROUGH = new PlayerSampleVerifier(mock(PlayerService.class), false);

    private static final class TestHarness implements AutoCloseable {
        final FakeMinecraftServer server;
        final List<SinkRecord> sink = new CopyOnWriteArrayList<>();
        final List<HoneypotDetector.SampleEntry> harvested = Collections.synchronizedList(new ArrayList<>());
        final InMemoryScanFingerprintStore store = new InMemoryScanFingerprintStore();

        TestHarness(FakeMinecraftServer server) {
            this.server = server;
        }

        ServerTrackerVerifier verifier(int window, int probeCap) {
            return verifier(window, probeCap, 50, 3, PASS_THROUGH);
        }

        ServerTrackerVerifier verifier(int window, int probeCap, int maxNets, int maxPorts) {
            return verifier(window, probeCap, maxNets, maxPorts, PASS_THROUGH);
        }

        ServerTrackerVerifier verifier(int window, int probeCap, PlayerSampleVerifier sampleVerifier) {
            return verifier(window, probeCap, 50, 3, sampleVerifier);
        }

        ServerTrackerVerifier verifier(int window, int probeCap, int maxNets, int maxPorts, PlayerSampleVerifier sampleVerifier) {
            HoneypotDetector detector = new HoneypotDetector(store, maxNets, 24, maxPorts);
            ServerTrackerVerifier.PlayerHarvester harvester = players -> {
                harvested.addAll(players);
                return players.size();
            };
            ServerTrackerVerifier.ServerTrackerSink sink = snapshot ->
                this.sink.add(new SinkRecord(snapshot.ip(), snapshot.port(), snapshot.players().size(), snapshot.honeypot()));
            return new ServerTrackerVerifier(new JavaMinecraftServerPinger(), detector, sink, harvester, sampleVerifier,
                    null, window, 65535, probeCap, 1_500);
        }

        @Override
        public void close() {
            server.close();
        }
    }

    @Test
    void findsGappedMultiServerHost() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0, 1, 6);
        String jsonBase = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        String jsonNext = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Alex", ALEX.toString()) + "]");
        String jsonFar = FakeMinecraftServer.statusJson("1.20", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("jeb_", JEB.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base, base + 1, base + 6), List.of(jsonBase, jsonNext, jsonFar)))) {
            ServerTrackerVerifier verifier = harness.verifier(10, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(3, result.serversFound(), "all three gapped servers must be found");
            assertEquals(3, result.playersEnqueued());
            assertFalse(result.honeypot());
            assertEquals(3, harness.sink.size(), "one sink record per discovered server");
            assertTrue(harness.sink.stream().anyMatch(r -> r.port() == base));
            assertTrue(harness.sink.stream().anyMatch(r -> r.port() == base + 1));
            assertTrue(harness.sink.stream().anyMatch(r -> r.port() == base + 6));
            assertEquals(3, harness.harvested.size());
        }
    }

    @Test
    void silentPortDoesNotReanchorTheWalk() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0, 1);
        String jsonBase = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base, base + 1), Arrays.asList(jsonBase, null)))) { // base+1 is a silent non-MC service
            ServerTrackerVerifier verifier = harness.verifier(3, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(1, result.serversFound(), "silent port must not count as a server");
            assertEquals(1, harness.sink.size());
            assertEquals(base, harness.sink.get(0).port());
            assertEquals(1, harness.harvested.size());
        }
    }

@Test
    void closedBasePortYieldsNoServers() throws Exception {
        int base = FakeMinecraftServer.findBasePort(1); // only base+1 is bound
        String json = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base + 1), List.of(json)))) {
            ServerTrackerVerifier verifier = harness.verifier(10, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(ServerTrackerVerifier.NOT_A_SERVER, result);
            assertTrue(harness.sink.isEmpty(), "no walk when the discovered port has no server");
            assertTrue(harness.harvested.isEmpty());
        }
    }

    @Test
    void discoversServerOnNonStandardDiscoveryPort() throws Exception {
        // A server running ONLY on 25566 (base+1): discovery probes 25564/25565/25566 and
        // hands off the open 25566, which must verify and harvest without any base port.
        int base = FakeMinecraftServer.findBasePort(1);
        String json = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base + 1), List.of(json)))) {
            ServerTrackerVerifier verifier = harness.verifier(3, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base + 1);

            assertEquals(1, result.serversFound());
            assertEquals(1, harness.sink.size());
            assertEquals(base + 1, harness.sink.get(0).port());
            assertEquals(1, harness.harvested.size());
        }
    }

    @Test
    void windowCapsTheWalk() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0, 5);
        String jsonBase = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        String jsonFar = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Alex", ALEX.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base, base + 5), List.of(jsonBase, jsonFar)))) {
            // Window 2: base+5 is beyond base+2, so the walk never reaches it.
            ServerTrackerVerifier verifier = harness.verifier(2, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(1, result.serversFound());
            assertEquals(1, harness.sink.size());
            assertEquals(base, harness.sink.get(0).port());
        }
    }

    @Test
    void offlineModePlayersAreFilteredOutOfTheHarvest() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0);
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes());
        String json = FakeMinecraftServer.statusJson("1.21", 2, 100,
                "[" + FakeMinecraftServer.sampleEntry("Cracked", offline.toString())
                        + "," + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base), List.of(json)))) {
            ServerTrackerVerifier verifier = harness.verifier(2, 200);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(1, result.serversFound());
            assertEquals(1, result.playersEnqueued(), "only the online-mode player is enqueued");
            assertEquals(1, harness.harvested.size());
            assertEquals(STEVE, harness.harvested.get(0).uuid());
            assertEquals(1, harness.sink.get(0).sampleCount());
        }
    }

    @Test
    void sameHostSampleOnManyPortsIsFlaggedAsHoneypot() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0, 1, 2);
        String json = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base, base + 1, base + 2), List.of(json, json, json)))) {
            ServerTrackerVerifier verifier = harness.verifier(3, 200, 50, 3);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(3, result.serversFound());
            assertTrue(result.honeypot(), "identical sample on 3 ports of one host flags the host");
            assertTrue(harness.harvested.size() < 3, "the repeated sample's players are dropped");
        }
    }

    @Test
    void flaggedBasePortSkipsTheWholeIp() throws Exception {
        int base = FakeMinecraftServer.findBasePort(0, 1, 2);
        String json = FakeMinecraftServer.statusJson("1.21", 1, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + "]");
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base, base + 1), List.of(json, json)))) {
            // max-identical-port-samples = 1 flags the host on the very first verified port.
            ServerTrackerVerifier verifier = harness.verifier(3, 200, 50, 1);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(1, result.serversFound(), "a flagged base port skips the port walk entirely");
            assertTrue(result.honeypot());
            assertEquals(0, harness.harvested.size(), "nothing from a flagged honeypot host is harvested");
            assertEquals(1, harness.sink.size(), "the flagged server itself is still recorded");
        }
    }

    @Test
    void fakeIdentitySamplesAreExcludedFromSinkAndHarvest() throws Exception {
        // Identity verification runs after the honeypot verdict and before the sink + harvest:
        // a sample dropped there must vanish from BOTH the persisted snapshot and the queue.
        int base = FakeMinecraftServer.findBasePort(0, 1);
        String json = FakeMinecraftServer.statusJson("1.21", 2, 100,
                "[" + FakeMinecraftServer.sampleEntry("Steve", STEVE.toString()) + ","
                        + FakeMinecraftServer.sampleEntry("Alex", ALEX.toString()) + "]");
        PlayerSampleVerifier dropsAlex = new PlayerSampleVerifier(mock(PlayerService.class), true) {
            @Override
            public List<HoneypotDetector.SampleEntry> verify(List<HoneypotDetector.SampleEntry> entries) {
                return entries.stream().filter(e -> !e.name().equals("Alex")).toList();
            }
        };
        try (TestHarness harness = new TestHarness(new FakeMinecraftServer(
                List.of(base), List.of(json)))) {
            ServerTrackerVerifier verifier = harness.verifier(10, 200, dropsAlex);
            ServerTrackerVerifier.HostResult result = verifier.verifyHost(IP, base);

            assertEquals(1, result.playersEnqueued(), "only verified identities reach the queue");
            assertEquals(1, harness.sink.get(0).sampleCount(), "the persisted snapshot carries only verified players");
            assertEquals(List.of("Steve"),
                    harness.harvested.stream().map(HoneypotDetector.SampleEntry::name).toList());
        }
    }
}