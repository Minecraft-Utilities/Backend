package xyz.mcutils.backend.service.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoneypotDetectorTest {

    private static final UUID V4_1 = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48");
    private static final UUID V4_2 = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final UUID V3 = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes());

    private final InMemoryScanFingerprintStore store = new InMemoryScanFingerprintStore();

    private HoneypotDetector detector(int maxNets, int maxPorts) {
        return new HoneypotDetector(store, maxNets, 24, maxPorts);
    }

    private static HoneypotDetector.SampleEntry entry(UUID uuid, String name) {
        return new HoneypotDetector.SampleEntry(uuid, name);
    }

    @Test
    void validSamplePassesThrough() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 2, 100,
                List.of(entry(V4_1, "Steve"), entry(V4_2, "Alex")));
        assertTrue(verdict.serverOk());
        assertFalse(verdict.honeypotServer());
        assertEquals(2, verdict.players().size());
        assertTrue(verdict.dropReasons().isEmpty());
    }

    @Test
    void offlineModeUuidIsDropped() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 1, 100,
                List.of(entry(V3, "Steve")));
        assertTrue(verdict.serverOk());
        assertFalse(verdict.honeypotServer());
        assertTrue(verdict.players().isEmpty());
        assertEquals(List.of("non_v4"), verdict.dropReasons());
    }

    @Test
    void malformedNameIsDropped() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 1, 100,
                List.of(entry(V4_1, "ba§d name!")));
        assertTrue(verdict.players().isEmpty());
        assertEquals(List.of("bad_name"), verdict.dropReasons());
    }

    @Test
    void duplicateUuidKeepsOnlyOneEntry() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 2, 100,
                List.of(entry(V4_1, "Steve"), entry(V4_1, "Alex")));
        assertEquals(1, verdict.players().size());
        assertTrue(verdict.dropReasons().contains("duplicate"));
    }

    @Test
    void sampleWithZeroOnlinePlayersIsDropped() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 0, 100,
                List.of(entry(V4_1, "Steve")));
        assertTrue(verdict.serverOk(), "the server itself is still valid");
        assertTrue(verdict.players().isEmpty());
        assertEquals(List.of("consistency"), verdict.dropReasons());
    }

    @Test
    void sampleLargerThanOnlineCountIsDropped() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 1, 100,
                List.of(entry(V4_1, "Steve"), entry(V4_2, "Alex")));
        assertTrue(verdict.players().isEmpty());
        assertTrue(verdict.dropReasons().contains("consistency"));
    }

    @Test
    void farmFingerprintIsBlockedAfterThresholdNets() {
        HoneypotDetector detector = detector(2, 3);
        List<HoneypotDetector.SampleEntry> sample = List.of(entry(V4_1, "Steve"));

        HoneypotDetector.Verdict first = detector.evaluate("1.2.3.4", 25565, 1, 100, sample);
        assertEquals(1, first.players().size(), "first /24 is under the threshold");

        HoneypotDetector.Verdict second = detector.evaluate("5.6.7.8", 25565, 1, 100, sample);
        assertTrue(second.players().isEmpty(), "second distinct /24 crosses the threshold");
        assertTrue(second.honeypotServer());
        assertTrue(second.dropReasons().contains("farm_blocked"));

        HoneypotDetector.Verdict third = detector.evaluate("9.10.11.12", 25565, 1, 100, sample);
        assertTrue(third.players().isEmpty(), "subsequent distinct /24s stay blocked");
    }

    @Test
    void sameFingerprintOnManyPortsOfOneIpIsFlagged() {
        HoneypotDetector detector = detector(50, 3);
        List<HoneypotDetector.SampleEntry> sample = List.of(entry(V4_1, "Steve"));

        assertEquals(1, detector.evaluate("1.2.3.4", 25565, 1, 100, sample).players().size());
        assertEquals(1, detector.evaluate("1.2.3.4", 25566, 1, 100, sample).players().size());
        HoneypotDetector.Verdict third = detector.evaluate("1.2.3.4", 25567, 1, 100, sample);
        assertTrue(third.players().isEmpty());
        assertTrue(third.honeypotServer());
        assertTrue(third.dropReasons().contains("ip_repeat"));
    }

    @Test
    void sameFingerprintSameNetDifferentIpsIsNotAFarm() {
        HoneypotDetector detector = detector(2, 3);
        List<HoneypotDetector.SampleEntry> sample = List.of(entry(V4_1, "Steve"));
        // Two hosts inside one /24: distinct IPs but the same net → not a farm.
        assertEquals(1, detector.evaluate("1.2.3.4", 25565, 1, 100, sample).players().size());
        assertEquals(1, detector.evaluate("1.2.3.99", 25565, 1, 100, sample).players().size());
    }

    @Test
    void invalidNamePartiallyDropsSample() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate(
                "1.2.3.4", 25565, 2, 100,
                List.of(entry(V4_1, "Steve"), entry(V4_2, "not valid!")));
        assertEquals(1, verdict.players().size());
        assertEquals(V4_1, verdict.players().get(0).uuid());
        assertEquals(List.of("bad_name"), verdict.dropReasons());
    }

    @Test
    void emptySamplePassesWithoutPlayers() {
        HoneypotDetector detector = detector(50, 3);
        HoneypotDetector.Verdict verdict = detector.evaluate("1.2.3.4", 25565, 5, 100, List.of());
        assertTrue(verdict.serverOk());
        assertTrue(verdict.players().isEmpty());
        assertFalse(verdict.honeypotServer());
    }
}