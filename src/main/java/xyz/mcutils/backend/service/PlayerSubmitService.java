package xyz.mcutils.backend.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.PlayerCreateSubmission;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid,} when no submitter).
 */
@Service
@Slf4j
public class PlayerSubmitService {

    private static final String REDIS_QUEUE_KEY = "player-submit-queue";
    private static final String REDIS_QUEUE_SET_KEY = "player-submit-queue-ids";
    private static final int BATCH_SIZE = 2500;
    private static final int ENQUEUE_CHUNK = 1000;
    private static final long EMPTY_QUEUE_BLOCK_SECONDS = 2;
    private static final int SUBMIT_WORKER_THREADS = 250;
    private static final int BULK_DRAIN_MAX = 500;
    private static final int BULK_CREATE_SIZE = 100;

    private final RedisTemplate<String, String> redis;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final Semaphore submitConcurrencyLimit = new Semaphore(SUBMIT_WORKER_THREADS);

    public static PlayerSubmitService INSTANCE;

    public PlayerSubmitService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis,
                               @Lazy PlayerService playerService, @Lazy MojangService mojangService) {
        this.redis = redis;
        this.playerService = playerService;
        this.mojangService = mojangService;
        INSTANCE = this;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        ListOperations<String, String> listOps = redis.opsForList();
        SetOperations<String, String> setOps = redis.opsForSet();

        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    List<String> batch = takeBatchFromQueue(BATCH_SIZE);
                    if (batch.isEmpty()) {
                        String one = listOps.leftPop(REDIS_QUEUE_KEY, EMPTY_QUEUE_BLOCK_SECONDS, TimeUnit.SECONDS);
                        if (one == null) {
                            Thread.sleep(Duration.ofSeconds(10).toMillis());
                            continue;
                        }
                        batch = new ArrayList<>(takeBatchFromQueue(BATCH_SIZE - 1));
                        batch.addFirst(one);
                    }
                    processBatch(batch, listOps, setOps);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Submit queue consumer error, continuing", e);
                }
            }
        });
    }

    /**
     * Atomically reads and removes up to batchSize items from the head of the queue.
     * 
     * @param batchSize the size of the batch to take
     * @return the list of raw entry strings
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> takeBatchFromQueue(int batchSize) {
        List<Object> results = redis.execute(new SessionCallback<>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.opsForList().range(REDIS_QUEUE_KEY, 0, batchSize - 1);
                operations.opsForList().trim(REDIS_QUEUE_KEY, batchSize, -1);
                return operations.exec();
            }
        });
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        Object first = results.getFirst();
        if (!(first instanceof List<?> list)) {
            return List.of();
        }
        RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        List<String> out = new ArrayList<>();
        for (Object elem : list) {
            if (elem instanceof String s) {
                out.add(s);
            } else if (elem instanceof byte[] bytes && valueSer != null) {
                String s = valueSer.deserialize(bytes);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * Processes a batch of items from the submit queue.
     * 
     * @param batch the list of raw entry strings
     * @param listOps the list operations
     * @param setOps the set operations
     */
    @SneakyThrows
    private void processBatch(List<String> batch, ListOperations<String, String> listOps, SetOperations<String, String> setOps) {
        List<QueueEntry> entries = new ArrayList<>();
        for (String raw : batch) {
            Optional<QueueEntry> entry = parseEntry(raw);
            if (entry.isEmpty()) {
                log.debug("Skipping invalid queue entry (e.g. legacy format): {}", raw.length() > 80 ? raw.substring(0, 80) + "..." : raw);
                continue;
            }
            entries.add(entry.get());
        }
        if (entries.isEmpty()) {
            return;
        }

        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        BulkCreateBuffer bulkBuffer = new BulkCreateBuffer(BULK_CREATE_SIZE, playerService);
        List<Future<?>> futures = new ArrayList<>();
        for (QueueEntry entry : entries) {
            if (existingIds.contains(entry.playerId())) {
                setOps.remove(REDIS_QUEUE_SET_KEY, entry.playerId().toString());
                continue;
            }
            String raw = formatEntry(entry.playerId(), entry.submittedBy());
            Future<?> future = Main.EXECUTOR.submit(() -> {
                submitConcurrencyLimit.acquireUninterruptibly();
                try {
                    processItem(entry, raw, listOps, setOps, bulkBuffer);
                } finally {
                    submitConcurrencyLimit.release();
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                log.warn("Submit task failed", e.getCause());
            }
        }
        bulkBuffer.flush();
    }

    /**
     * Processes an item from the submit queue.
     *
     * @param entry the queue entry
     * @param rawEntry the raw entry string
     * @param listOps the list operations
     * @param setOps the set operations
     * @param bulkBuffer the buffer for bulk create (add submission on success)
     */
    private void processItem(QueueEntry entry, String rawEntry, ListOperations<String, String> listOps, SetOperations<String, String> setOps, BulkCreateBuffer bulkBuffer) {
        UUID playerId = entry.playerId();
        UUID submittedBy = entry.submittedBy();
        boolean requeued = false;
        try {
            if (playerService.exists(playerId)) {
                return;
            }
            MojangProfileToken token = mojangService.getProfile(playerId.toString());
            if (token == null) {
                log.warn("Player with uuid '{}' was not found", playerId);
                return;
            }
            bulkBuffer.add(new PlayerCreateSubmission(token, submittedBy));
        } catch (NotFoundException ignored) {
            // fall through to finally
        } catch (MojangAPIRateLimitException e) {
            listOps.rightPush(REDIS_QUEUE_KEY, rawEntry);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(150));
            requeued = true;
        } finally {
            if (!requeued) {
                setOps.remove(REDIS_QUEUE_SET_KEY, playerId.toString());
            }
        }
    }

    /**
     * Submits a list of players to the submit queue.
     * 
     * @param players the list of player identifiers to submit
     * @param submittedBy the submitted by player id string
     */
    public void submitPlayers(List<String> players, String submittedBy) {
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;

        List<UUID> toEnqueue = parseAndFilterInputUuids(players);
        if (toEnqueue.isEmpty()) {
            return;
        }

        toEnqueue = filterExistingInDb(toEnqueue);
        if (toEnqueue.isEmpty()) {
            return;
        }

        List<String> entryStrings = new ArrayList<>();
        List<String> playerIdStrings = new ArrayList<>();
        filterAlreadyInQueue(toEnqueue, by, entryStrings, playerIdStrings);
        if (entryStrings.isEmpty()) {
            return;
        }

        enqueueChunked(entryStrings, playerIdStrings, submittedBy);
    }

    /**
     * Parses and filters the input uuids.
     * 
     * @param players the list of player identifiers to parse
     * @return the list of uuids that are not in the database
     */
    private List<UUID> parseAndFilterInputUuids(List<String> players) {
        return players.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> UUIDUtils.parseUuid(id.trim()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }


    /**
     * Filters out players that are already in the database.
     * 
     * @param uuids the list of uuids to filter
     * @return the list of uuids that are not in the database
     */
    private List<UUID> filterExistingInDb(List<UUID> uuids) {
        Set<UUID> existingIds = playerService.getExistingPlayerIds(uuids);
        return uuids.stream()
                .filter(uuid -> !existingIds.contains(uuid))
                .toList();
    }

    /**
     * Filters out players that are already in the queue.
     * 
     * @param toEnqueue the list of uuids to filter
     * @param submittedBy the submitted by player id
     * @param entryStringsOut the list of entry strings to enqueue
     * @param playerIdStringsOut the list of player id strings to enqueue
     */
    @SuppressWarnings("unchecked")
    private void filterAlreadyInQueue(List<UUID> toEnqueue, UUID submittedBy,
                                      List<String> entryStringsOut, List<String> playerIdStringsOut) {
        RedisSerializer<String> keySer = (RedisSerializer<String>) redis.getKeySerializer();
        RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        byte[] keyBytes = keySer.serialize(REDIS_QUEUE_SET_KEY);
        if (keyBytes == null) {
            return;
        }

        for (int chunkStart = 0; chunkStart < toEnqueue.size(); chunkStart += ENQUEUE_CHUNK) {
            int chunkEnd = Math.min(chunkStart + ENQUEUE_CHUNK, toEnqueue.size());
            List<UUID> chunk = toEnqueue.subList(chunkStart, chunkEnd);
            byte[][] memberBytes = new byte[chunk.size()][];
            for (int i = 0; i < chunk.size(); i++) {
                memberBytes[i] = valueSer.serialize(chunk.get(i).toString());
            }
            List<Boolean> inQueue = redis.execute((RedisConnection connection) ->
                    connection.setCommands().sMIsMember(keyBytes, memberBytes));
            if (inQueue == null) {
                inQueue = List.of();
            }
            for (int i = 0; i < chunk.size(); i++) {
                if (Boolean.TRUE.equals(inQueue.get(i))) {
                    continue;
                }
                UUID uuid = chunk.get(i);
                entryStringsOut.add(formatEntry(uuid, submittedBy));
                playerIdStringsOut.add(uuid.toString());
            }
        }
    }

    /**
     * Enqueues a list of players to the submit queue in chunks to avoid huge single commands.
     * 
     * @param entryStrings the list of entry strings to enqueue
     * @param playerIdStrings the list of player id strings to enqueue
     * @param submittedBy the submitted by player id string
     */
    private void enqueueChunked(List<String> entryStrings, List<String> playerIdStrings, String submittedBy) {
        ListOperations<String, String> listOps = redis.opsForList();
        SetOperations<String, String> setOps = redis.opsForSet();
        for (int i = 0; i < entryStrings.size(); i += ENQUEUE_CHUNK) {
            int end = Math.min(i + ENQUEUE_CHUNK, entryStrings.size());
            listOps.rightPushAll(REDIS_QUEUE_KEY, entryStrings.subList(i, end));
            setOps.add(REDIS_QUEUE_SET_KEY, playerIdStrings.subList(i, end).toArray(new String[0]));
        }
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})",
                entryStrings.size(), size != null ? size : 0, submittedBy);
    }

    /**
     * Returns the current size of the submission queue (for metrics).
     * 
     * @return the current size of the submission queue
     */
    public long getSubmissionQueueSize() {
        Long size = redis.opsForList().size(REDIS_QUEUE_KEY);
        return size != null ? size : 0L;
    }

    /**
     * Formats a queue entry string.
     * 
     * @param playerId the player id
     * @param submittedBy the submitted by player id
     * @return the queue entry string
     */
    private String formatEntry(UUID playerId, UUID submittedBy) {
        return playerId + "," + (submittedBy != null ? submittedBy : "");
    }

    /**
     * Parses a queue entry string. Returns empty if format is invalid (e.g. legacy JSON).
     * 
     * @param s the queue entry string
     * @return the queue entry
     */
    private Optional<QueueEntry> parseEntry(String s) {
        if (s == null || !s.contains(",")) {
            return Optional.empty();
        }
        String[] parts = s.split(",", 2);
        UUID playerId = UUIDUtils.parseUuid(parts[0].trim());
        if (playerId == null) {
            return Optional.empty();
        }
        UUID submittedBy = null;
        if (parts.length > 1 && !parts[1].isBlank()) {
            submittedBy = UUIDUtils.parseUuid(parts[1].trim());
        }
        return Optional.of(new QueueEntry(playerId, submittedBy));
    }

    /**
     * Thread-safe buffer that batches {@link PlayerCreateSubmission}s and flushes via
     * {@link PlayerService#createPlayers(java.util.List)} when size reaches threshold or on {@link #flush()}.
     */
    private static final class BulkCreateBuffer {
        private final Object lock = new Object();
        private final List<PlayerCreateSubmission> buffer = new ArrayList<>();
        private final int batchSize;
        private final PlayerService playerService;

        BulkCreateBuffer(int batchSize, PlayerService playerService) {
            this.batchSize = batchSize;
            this.playerService = playerService;
        }

        void add(PlayerCreateSubmission item) {
            List<PlayerCreateSubmission> toFlush = null;
            synchronized (lock) {
                buffer.add(item);
                if (buffer.size() >= batchSize) {
                    int drain = Math.min(buffer.size(), BULK_DRAIN_MAX);
                    toFlush = new ArrayList<>(buffer.subList(0, drain));
                    buffer.subList(0, drain).clear();
                }
            }
            if (toFlush != null) {
                playerService.createPlayers(toFlush);
            }
        }

        void flush() {
            List<PlayerCreateSubmission> toFlush;
            synchronized (lock) {
                toFlush = buffer.isEmpty() ? null : new ArrayList<>(buffer);
                buffer.clear();
            }
            if (toFlush != null) {
                playerService.createPlayers(toFlush);
            }
        }
    }

    /**
     * A queue entry.
     *
     * @param playerId the player id
     * @param submittedBy the submitted by player id
     */
    private record QueueEntry(UUID playerId, UUID submittedBy) { }
}
