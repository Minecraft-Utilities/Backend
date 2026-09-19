package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.service.PlayerService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link PlayerSampleVerifier} (cache-only): a sample entry is kept exactly when
 * {@link PlayerService#getCachedPlayer} already knows the uuid under the sampled name — the
 * players table is the Mojang-verified identity store, and no Mojang call happens here.
 * Metrics are absent in tests (the metric registry is not initialized), which the verifier
 * treats as optional.
 */
class PlayerSampleVerifierTest {

    private static final UUID STEVE_UUID = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48");
    private static final UUID ALEX_UUID = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    private PlayerSampleVerifier verifier(PlayerService playerService, boolean enabled) {
        return new PlayerSampleVerifier(playerService, enabled);
    }

    private HoneypotDetector.SampleEntry entry(UUID uuid, String name) {
        return new HoneypotDetector.SampleEntry(uuid, name);
    }

    private PlayerRow knownPlayer(String username) {
        PlayerRow row = mock(PlayerRow.class);
        when(row.getUsername()).thenReturn(username);
        return row;
    }

    @Test
    void knownPlayerWithMatchingNameIsVerified() {
        PlayerService playerService = mock(PlayerService.class);
        PlayerRow row = knownPlayer("Steve");
        when(playerService.getCachedPlayer(STEVE_UUID.toString())).thenReturn(Optional.of(row));
        PlayerSampleVerifier verifier = verifier(playerService, true);

        List<HoneypotDetector.SampleEntry> verified = verifier.verify(List.of(entry(STEVE_UUID, "steve")));

        assertEquals(1, verified.size(), "case-insensitive match against the stored username");
    }

    @Test
    void knownPlayerWithDifferentStoredNameIsDroppedAsFake() {
        PlayerService playerService = mock(PlayerService.class);
        PlayerRow row = knownPlayer("Alex");
        when(playerService.getCachedPlayer(STEVE_UUID.toString())).thenReturn(Optional.of(row));
        PlayerSampleVerifier verifier = verifier(playerService, true);

        assertTrue(verifier.verify(List.of(entry(STEVE_UUID, "Steve"))).isEmpty(),
                "stored name differs -> the sampled pair no longer matches identity");
    }

    @Test
    void unknownPlayerIsDroppedAsUnverified() {
        PlayerService playerService = mock(PlayerService.class);
        when(playerService.getCachedPlayer(STEVE_UUID.toString())).thenReturn(Optional.empty());
        PlayerSampleVerifier verifier = verifier(playerService, true);

        assertTrue(verifier.verify(List.of(entry(STEVE_UUID, "Steve"))).isEmpty(),
                "cache-only gate cannot verify identities the players table does not know");
    }

    @Test
    void disabledVerifierPassesEverythingThrough() {
        PlayerService playerService = mock(PlayerService.class);
        PlayerSampleVerifier verifier = verifier(playerService, false);

        List<HoneypotDetector.SampleEntry> entries = List.of(entry(STEVE_UUID, "Steve"), entry(ALEX_UUID, "Alex"));
        assertEquals(entries, verifier.verify(entries));
        verify(playerService, never()).getCachedPlayer(STEVE_UUID.toString());
    }

    @Test
    void mixedBatchKeepsOnlyKnownMatchingIdentities() {
        PlayerService playerService = mock(PlayerService.class);
        PlayerRow row = knownPlayer("Steve");
        when(playerService.getCachedPlayer(STEVE_UUID.toString())).thenReturn(Optional.of(row));
        when(playerService.getCachedPlayer(ALEX_UUID.toString())).thenReturn(Optional.empty());
        PlayerSampleVerifier verifier = verifier(playerService, true);

        List<HoneypotDetector.SampleEntry> verified = verifier.verify(List.of(
                entry(STEVE_UUID, "Steve"), entry(ALEX_UUID, "Alex")));

        assertEquals(1, verified.size());
        assertEquals(STEVE_UUID, verified.get(0).uuid(), "known identity kept, unknown dropped");
    }
}