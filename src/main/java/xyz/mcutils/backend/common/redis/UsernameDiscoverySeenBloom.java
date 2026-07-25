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
    private final long expectedInsertions;
    private final double falsePositiveProbability;
    private BloomFilter<String> filter;
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
        this.expectedInsertions = expectedInsertions;
        this.falsePositiveProbability = falsePositiveProbability;
        this.filter = BloomFilter.create(FUNNEL, expectedInsertions, falsePositiveProbability);
        log.info(
                "Username discovery seen bloom initialized (~{} inserts @ {} FPP, ~{} MiB in memory, persist={})",
                expectedInsertions,
                falsePositiveProbability,
                approximateMemoryBytes() / (1024 * 1024),
                persistToRedis
        );
    }

    /**
     * Loads a previously persisted bloom filter from Redis. Safe to call from a background thread;
     * does not block application startup.
     */
    public void tryLoadFromRedis() {
        if (!persistToRedis) {
            warnIfLegacySeenSetPresent();
            return;
        }
        try {
            byte[] bytes = readRedisBytes(redisKey);
            if (bytes == null || bytes.length == 0) {
                log.info("No persisted username discovery bloom at '{}', using in-memory filter", redisKey);
                warnIfLegacySeenSetPresent();
                return;
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                BloomFilter<String> loaded = BloomFilter.readFrom(in, FUNNEL);
                synchronized (lock) {
                    this.filter = loaded;
                    this.dirty = false;
                }
                log.info(
                        "Loaded username discovery bloom from Redis ({} bytes, ~{} entries)",
                        bytes.length,
                        approximateElementCount()
                );
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to load username discovery bloom from Redis key '{}', continuing with in-memory filter",
                    redisKey,
                    e
            );
        }
        warnIfLegacySeenSetPresent();
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
            try {
                writeToRedis();
                dirty = false;
            } catch (Exception e) {
                log.warn("Failed to persist username discovery bloom to Redis key '{}'", redisKey, e);
            }
        }
    }

    private void writeToRedis() throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filter.writeTo(out);
            bytes = out.toByteArray();
        }
        RedisSerializer<String> keySerializer = redis.getStringSerializer();
        byte[] keyBytes = keySerializer.serialize(redisKey);
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(keyBytes, bytes);
            return null;
        });
        log.debug("Persisted username discovery bloom to Redis ({} bytes)", bytes.length);
    }

    private byte[] readRedisBytes(String key) {
        RedisSerializer<String> keySerializer = redis.getStringSerializer();
        byte[] keyBytes = keySerializer.serialize(key);
        return redis.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(keyBytes));
    }

    private void warnIfLegacySeenSetPresent() {
        try {
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
        } catch (Exception e) {
            log.debug("Could not check legacy username discovery seen set: {}", e.getMessage());
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
            bloom.flushToRedisIfDirty();
        }
        bloom.flushToRedisIfDirty();
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
