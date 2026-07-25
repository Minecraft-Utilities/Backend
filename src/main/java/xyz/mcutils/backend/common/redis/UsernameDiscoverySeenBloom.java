package xyz.mcutils.backend.common.redis;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Fixed-size probabilistic "seen" filter for username discovery.
 * <p>
 * Memory use is bounded by {@code expectedInsertions} and {@code falsePositiveProbability}.
 * False positives skip a candidate (rare at configured FPP); false negatives never occur.
 */
@Slf4j
public final class UsernameDiscoverySeenBloom {
    private static final Funnel<CharSequence> FUNNEL = Funnels.stringFunnel(StandardCharsets.UTF_8);
    private static final String LEGACY_SEEN_SET_KEY = "username-discovery-seen";

    private final RedisTemplate<String, String> redis;
    private final String redisKey;
    private final boolean persistToRedis;
    private final BloomFilter<String> filter;
    private final Object lock = new Object();
    private volatile boolean dirty;

    public UsernameDiscoverySeenBloom(
            RedisTemplate<String, String> redis,
            String redisKey,
            long expectedInsertions,
            double falsePositiveProbability,
            boolean persistToRedis
    ) {
        this.redis = redis;
        this.redisKey = redisKey;
        this.persistToRedis = persistToRedis;
        this.filter = loadOrCreate(expectedInsertions, falsePositiveProbability);
        warnIfLegacySeenSetPresent();
        log.info(
                "Username discovery seen bloom ready (~{} inserts @ {} FPP, ~{} MiB in memory, persist={})",
                expectedInsertions,
                falsePositiveProbability,
                approximateMemoryBytes() / (1024 * 1024),
                persistToRedis
        );
    }

    public List<Boolean> mightContainLowercase(List<String> lowercaseUsernames) {
        if (lowercaseUsernames.isEmpty()) {
            return List.of();
        }
        synchronized (lock) {
            List<Boolean> result = new ArrayList<>(lowercaseUsernames.size());
            for (String name : lowercaseUsernames) {
                result.add(filter.mightContain(normalize(name)));
            }
            return result;
        }
    }

    public void markSeenLowercase(Collection<String> lowercaseUsernames) {
        if (lowercaseUsernames == null || lowercaseUsernames.isEmpty()) {
            return;
        }
        synchronized (lock) {
            for (String name : lowercaseUsernames) {
                filter.put(normalize(name));
            }
            dirty = true;
        }
    }

    public long approximateElementCount() {
        synchronized (lock) {
            return filter.approximateElementCount();
        }
    }

    public long approximateMemoryBytes() {
        synchronized (lock) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                filter.writeTo(out);
                return out.size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public void flushToRedisIfDirty() {
        if (!persistToRedis || !dirty) {
            return;
        }
        synchronized (lock) {
            if (!dirty) {
                return;
            }
            writeToRedis();
            dirty = false;
        }
    }

    private BloomFilter<String> loadOrCreate(long expectedInsertions, double falsePositiveProbability) {
        if (!persistToRedis) {
            return BloomFilter.create(FUNNEL, expectedInsertions, falsePositiveProbability);
        }
        byte[] bytes = readRedisBytes(redisKey);
        if (bytes == null || bytes.length == 0) {
            return BloomFilter.create(FUNNEL, expectedInsertions, falsePositiveProbability);
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return BloomFilter.readFrom(in, FUNNEL);
        } catch (IOException e) {
            log.warn("Failed to load username discovery bloom from Redis key '{}', creating a new filter", redisKey, e);
            return BloomFilter.create(FUNNEL, expectedInsertions, falsePositiveProbability);
        }
    }

    private void writeToRedis() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filter.writeTo(out);
            byte[] bytes = out.toByteArray();
            RedisSerializer<String> keySerializer = redis.getStringSerializer();
            byte[] keyBytes = keySerializer.serialize(redisKey);
            redis.execute((RedisCallback<Void>) connection -> {
                connection.stringCommands().set(keyBytes, bytes);
                return null;
            });
            log.debug("Persisted username discovery bloom to Redis ({} bytes)", bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] readRedisBytes(String key) {
        RedisSerializer<String> keySerializer = redis.getStringSerializer();
        byte[] keyBytes = keySerializer.serialize(key);
        return redis.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(keyBytes));
    }

    private void warnIfLegacySeenSetPresent() {
        Long legacySize = redis.opsForSet().size(LEGACY_SEEN_SET_KEY);
        if (legacySize != null && legacySize > 0) {
            log.warn(
                    "Legacy Redis set '{}' still has {} members and may be using significant RAM. "
                            + "Delete it after deploying the bloom filter: DEL {}",
                    LEGACY_SEEN_SET_KEY,
                    legacySize,
                    LEGACY_SEEN_SET_KEY
            );
        }
    }

    private static String normalize(String lowercaseUsername) {
        return lowercaseUsername.toLowerCase(Locale.ROOT);
    }

    public static void sleepFlushLoop(UsernameDiscoverySeenBloom bloom, Duration interval, BooleanSupplier isRunning) {
        while (isRunning.getAsBoolean()) {
            sleepQuietly(interval);
            if (!isRunning.getAsBoolean()) {
                break;
            }
            try {
                bloom.flushToRedisIfDirty();
            } catch (Exception e) {
                log.warn("Failed to persist username discovery bloom to Redis", e);
            }
        }
        try {
            bloom.flushToRedisIfDirty();
        } catch (Exception e) {
            log.warn("Failed to persist username discovery bloom on shutdown", e);
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
