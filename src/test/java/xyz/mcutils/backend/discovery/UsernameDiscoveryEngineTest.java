package xyz.mcutils.backend.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernameDiscoveryEngineTest {
    private final UsernameDiscoveryEngine engine = new UsernameDiscoveryEngine(new UsernameDiscoveryConfig(
            750,
            99,
            999,
            16,
            12,
            List.of("_", "1", "123"),
            UsernameDiscoveryConfig.DEFAULT_PREFIXES,
            UsernameDiscoveryConfig.DEFAULT_AFFIXES,
            UsernameDiscoveryConfig.DEFAULT_BIRTH_YEARS
    ));

    @Test
    void generateCandidates_includesSuffixAndStrippedBaseVariants() {
        Set<String> candidates = names(engine.generateCandidates("shadow_king_12"));

        assertTrue(candidates.contains("shadow_king_12_"));
        assertTrue(candidates.contains("shadow_king_121"));
        assertTrue(candidates.contains("shadow_king_"));
        assertTrue(candidates.contains("shadow_king1"));
    }

    @Test
    void generateCandidates_includesWordPermutations() {
        Set<String> candidates = names(engine.generateCandidates("shadow_king_12"));

        assertTrue(candidates.contains("shadow"));
        assertTrue(candidates.contains("king"));
        assertTrue(candidates.contains("shadow_king"));
        assertTrue(candidates.contains("king_shadow"));
        assertFalse(candidates.contains("12"));
    }

    @Test
    void generateCandidates_includesIncrementedNumericSuffix() {
        Set<String> candidates = names(engine.generateCandidates("shadow_king_12"));

        assertTrue(candidates.contains("shadow_king_13"));
        assertTrue(candidates.contains("shadow_king_11"));
    }

    @Test
    void generateCandidates_tagsStrategies() {
        List<UsernameCandidate> candidates = engine.generateCandidates("shadow_king_12");

        assertTrue(candidates.stream().allMatch(c -> c.strategy() != null));
        assertTrue(candidates.stream().anyMatch(c ->
                c.username().equals("king_shadow") && c.strategy() == UsernameDiscoveryStrategy.WORD_PAIR));
        assertTrue(candidates.stream().map(UsernameCandidate::strategy).distinct().count() >= 5);
    }

    @Test
    void isValidCandidate_enforcesMinecraftLengthRules() {
        assertTrue(UsernameDiscoveryEngine.isValidCandidate("alex"));
        assertFalse(UsernameDiscoveryEngine.isValidCandidate("ab"));
        assertFalse(UsernameDiscoveryEngine.isValidCandidate("a".repeat(17)));
    }

    @Test
    void queuePayload_roundTripsWithStrategy() {
        UsernameCandidate original = new UsernameCandidate("shadow_king", UsernameDiscoveryStrategy.WORD_PAIR);
        UsernameCandidate parsed = UsernameCandidate.parseQueuePayload(original.queuePayload());

        assertTrue(parsed.username().equals("shadow_king"));
        assertTrue(parsed.strategy() == UsernameDiscoveryStrategy.WORD_PAIR);
    }

    private static Set<String> names(List<UsernameCandidate> candidates) {
        return candidates.stream().map(UsernameCandidate::username).collect(Collectors.toSet());
    }
}
