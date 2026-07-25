package xyz.mcutils.backend.common.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsernameDiscoverySeenBloomTest {
    @Test
    void mightContainTracksMarkedNames() {
        UsernameDiscoverySeenBloom bloom = newBloom();

        assertFalse(bloom.mightContainLowercase(List.of("shadow_king")).getFirst());
        bloom.markSeenLowercase(List.of("shadow_king"));
        assertTrue(bloom.mightContainLowercase(List.of("shadow_king")).getFirst());
    }

    @Test
    void normalizesCaseWhenMarkingAndChecking() {
        UsernameDiscoverySeenBloom bloom = newBloom();

        bloom.markSeenLowercase(List.of("Shadow_King"));
        assertTrue(bloom.mightContainLowercase(List.of("shadow_king")).getFirst());
    }

    @SuppressWarnings("unchecked")
    private static UsernameDiscoverySeenBloom newBloom() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.size("username-discovery-seen")).thenReturn(0L);
        return new UsernameDiscoverySeenBloom(redis, "username-discovery-seen-bloom-test", 10_000, 0.01, false);
    }
}
