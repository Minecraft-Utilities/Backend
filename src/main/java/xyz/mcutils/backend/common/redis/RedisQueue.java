package xyz.mcutils.backend.common.redis;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redis-backed FIFO queue using a list, with an optional companion set for O(1) deduplication.
 * <p>
 * List key: {@code queueName}; dedupe set key: {@code queueName + "-ids"} when deduplication is enabled.
 */
public final class RedisQueue {

    private final RedisTemplate<String, String> redis;
    private final String listKey;
    private final String dedupeSetKey;
    private final boolean deduplicationEnabled;

    public RedisQueue(RedisTemplate<String, String> redis, String queueName) {
        this(redis, queueName, queueName + "-ids", true);
    }

    public RedisQueue(RedisTemplate<String, String> redis, String listKey, String dedupeSetKey, boolean deduplicationEnabled) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.listKey = Objects.requireNonNull(listKey, "listKey");
        this.dedupeSetKey = dedupeSetKey;
        this.deduplicationEnabled = deduplicationEnabled && dedupeSetKey != null;
    }

    public String listKey() {
        return listKey;
    }

    public String dedupeSetKey() {
        return dedupeSetKey;
    }

    public boolean deduplicationEnabled() {
        return deduplicationEnabled;
    }

    /**
     * Enqueues items whose dedupe keys are not already in the companion set.
     */
    public EnqueueResult enqueue(List<QueueItem> items) {
        if (items == null || items.isEmpty()) {
            return EnqueueResult.EMPTY;
        }
        List<String> payloads = new ArrayList<>();
        List<String> dedupeKeys = new ArrayList<>();
        int alreadyQueued = 0;

        if (deduplicationEnabled) {
            List<Boolean> present = areDedupeKeysPresent(items.stream().map(QueueItem::dedupeKey).toList());
            for (int i = 0; i < items.size(); i++) {
                if (Boolean.TRUE.equals(present.get(i))) {
                    alreadyQueued++;
                } else {
                    QueueItem item = items.get(i);
                    payloads.add(item.payload());
                    dedupeKeys.add(item.dedupeKey());
                }
            }
        } else {
            for (QueueItem item : items) {
                payloads.add(item.payload());
            }
        }

        if (payloads.isEmpty()) {
            return new EnqueueResult(0, alreadyQueued);
        }

        ListOperations<String, String> listOps = redis.opsForList();
        listOps.rightPushAll(listKey, payloads);
        if (deduplicationEnabled) {
            redis.opsForSet().add(dedupeSetKey, dedupeKeys.toArray(new String[0]));
        }
        return new EnqueueResult(payloads.size(), alreadyQueued);
    }

    public void enqueueUnchecked(Collection<String> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        redis.opsForList().rightPushAll(listKey, payloads.toArray(new String[0]));
    }

    public void requeue(Collection<String> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        redis.opsForList().rightPushAll(listKey, payloads.toArray(new String[0]));
    }

    public void requeue(String payload) {
        if (payload != null && !payload.isBlank()) {
            redis.opsForList().rightPush(listKey, payload);
        }
    }

    public List<Boolean> areDedupeKeysPresent(List<String> dedupeKeys) {
        if (!deduplicationEnabled || dedupeKeys == null || dedupeKeys.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        RedisSerializer<String> keySer = (RedisSerializer<String>) redis.getKeySerializer();
        @SuppressWarnings("unchecked")
        RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        byte[] keyBytes = keySer.serialize(dedupeSetKey);
        byte[][] memberBytes = new byte[dedupeKeys.size()][];
        for (int i = 0; i < dedupeKeys.size(); i++) {
            memberBytes[i] = valueSer.serialize(dedupeKeys.get(i));
        }
        List<Boolean> result = redis.execute((RedisConnection connection) ->
                connection.setCommands().sMIsMember(keyBytes, memberBytes));
        return result != null ? result : List.of();
    }

    public void removeDedupeKeys(Collection<String> dedupeKeys) {
        if (!deduplicationEnabled || dedupeKeys == null || dedupeKeys.isEmpty()) {
            return;
        }
        redis.opsForSet().remove(dedupeSetKey, dedupeKeys.toArray());
    }

    public Optional<String> blockingPopFirst(Duration timeout) {
        String value = redis.opsForList().leftPop(listKey, timeout.toSeconds(), TimeUnit.SECONDS);
        return Optional.ofNullable(value);
    }

    public List<String> popUpTo(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> result = redis.opsForList().leftPop(listKey, count);
        return result != null ? result : List.of();
    }

    /**
     * Blocks until an item is available or {@code emptyTimeout} elapses, then drains up to {@code batchSize} items.
     */
    public Optional<List<String>> blockingTakeBatch(int batchSize, Duration emptyTimeout) {
        if (batchSize <= 0) {
            return Optional.empty();
        }
        return blockingPopFirst(emptyTimeout).map(first -> {
            List<String> batch = new ArrayList<>(batchSize);
            batch.add(first);
            if (batchSize > 1) {
                batch.addAll(popUpTo(batchSize - 1));
            }
            return batch;
        });
    }

    public long size() {
        Long size = redis.opsForList().size(listKey);
        return size != null ? size : 0L;
    }

    /**
     * Runs a blocking consumer loop until {@code running} is false.
     */
    public void consumeBatches(
            AtomicBoolean running,
            int batchSize,
            Duration emptyBlockTimeout,
            Consumer<List<String>> batchProcessor) {
        Objects.requireNonNull(running, "running");
        Objects.requireNonNull(batchProcessor, "batchProcessor");
        while (running.get()) {
            try {
                Optional<List<String>> batch = blockingTakeBatch(batchSize, emptyBlockTimeout);
                if (batch.isEmpty()) {
                    continue;
                }
                batchProcessor.accept(batch.get());
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                throw e;
            }
        }
    }

    public ListOperations<String, String> listOps() {
        return redis.opsForList();
    }

    public SetOperations<String, String> setOps() {
        return redis.opsForSet();
    }

    public record QueueItem(String payload, String dedupeKey) {
        public QueueItem {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(dedupeKey, "dedupeKey");
        }

        public static QueueItem of(String payload) {
            return new QueueItem(payload, payload);
        }
    }

    public record EnqueueResult(int enqueued, int alreadyQueued) {
        public static final EnqueueResult EMPTY = new EnqueueResult(0, 0);
    }
}
