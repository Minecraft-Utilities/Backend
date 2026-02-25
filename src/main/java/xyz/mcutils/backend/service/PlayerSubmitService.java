package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.redis.SubmitQueueItem;
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

    private final Semaphore submitConcurrencyLimit = new Semaphore(SUBMIT_WORKER_THREADS);

    public static PlayerSubmitService INSTANCE;

    private final RedisTemplate<String, SubmitQueueItem> submitQueueTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final MongoTemplate mongoTemplate;

    public PlayerSubmitService(RedisTemplate<String, SubmitQueueItem> submitQueueTemplate, RedisTemplate<String, Object> redisTemplate,
                               @Lazy PlayerService playerService, @Lazy MojangService mojangService, MongoTemplate mongoTemplate) {
        this.submitQueueTemplate = submitQueueTemplate;
        this.redisTemplate = redisTemplate;
        this.playerService = playerService;
        this.mojangService = mojangService;
        this.mongoTemplate = mongoTemplate;
        INSTANCE = this;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        ListOperations<String, SubmitQueueItem> listOps = submitQueueTemplate.opsForList();
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    List<SubmitQueueItem> batch = takeBatchFromQueue(BATCH_SIZE);
                    if (batch.isEmpty()) {
                        SubmitQueueItem one = listOps.leftPop(REDIS_QUEUE_KEY, EMPTY_QUEUE_BLOCK_SECONDS, TimeUnit.SECONDS);
                        if (one == null) {
                            Thread.sleep(Duration.ofSeconds(60).toMillis());
                            continue;
                        }
                        batch = new ArrayList<>(takeBatchFromQueue(BATCH_SIZE - 1));
                        batch.addFirst(one);
                    }

                    // One bulk existence check; skip and dequeue items that already exist in DB
                    Set<UUID> existingIds = playerService.getExistingPlayerIds(batch.stream().map(SubmitQueueItem::id).toList());
                    List<Future<?>> futures = new ArrayList<>();
                    for (SubmitQueueItem item : batch) {
                        if (existingIds.contains(item.id())) {
                            setOps.remove(REDIS_QUEUE_SET_KEY, item.id().toString());
                            continue;
                        }
                        Future<?> future = Main.EXECUTOR.submit(() -> {
                            submitConcurrencyLimit.acquireUninterruptibly();
                            try {
                                processItem(item, listOps, setOps);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Atomically reads and removes up to x items from the head of the queue.
     * Uses a Redis transaction (LRANGE + LTRIM) so items are not processed twice.
     * 
     * @param batchSize the size of the batch to take
     * @return the batch of items
     */
    @SuppressWarnings("unchecked")
    private List<SubmitQueueItem> takeBatchFromQueue(int batchSize) {
        List<Object> results = submitQueueTemplate.execute(new SessionCallback<>() {
            @Override
            public List<Object> execute(@NonNull RedisOperations operations) {
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
        return first instanceof List<?> list ? (List<SubmitQueueItem>) list : List.of();
    }

    /**
     * Processes a single item from the submit queue.
     *
     * @param item the item to process
     * @param listOps the list operations
     * @param setOps the set operations
     */
    private void processItem(SubmitQueueItem item, ListOperations<String, SubmitQueueItem> listOps, SetOperations<String, Object> setOps) {
        UUID id = item.id();
        UUID submittedBy = item.submittedBy();
        boolean requeued = false;
        try {
            if (this.playerService.exists(id)) {
                return;
            }
            MojangProfileToken token = this.mojangService.getProfile(id.toString());
            if (token == null) {
                log.warn("Player with uuid '{}' was not found", id);
                return;
            }
            this.playerService.createPlayer(token);
            if (submittedBy != null) {
                this.mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(submittedBy)),
                        new Update().inc("submittedUuids", 1),
                        PlayerDocument.class
                );
            }
        } catch (NotFoundException ignored) {
            // fall through to finally
        } catch (MojangAPIRateLimitException e) {
            listOps.rightPush(REDIS_QUEUE_KEY, item);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(150));
            requeued = true;
        } finally {
            if (!requeued) {
                setOps.remove(REDIS_QUEUE_SET_KEY, id.toString());
            }
        }
    }

    /**
     * Submits a list of players to the submit queue.
     *
     * @param players the list of players to submit
     * @param submittedBy the identifier for who submitted
     */
    public void submitPlayers(List<String> players, String submittedBy) {
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;

        // Parse and collect valid UUIDs
        List<UUID> uuids = players.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> UUIDUtils.parseUuid(id.trim()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uuids.isEmpty()) {
            return;
        }

        // Batch check which already exist in DB
        Set<UUID> existingIds = this.playerService.getExistingPlayerIds(uuids);
        List<UUID> toEnqueue = uuids.stream()
                .filter(uuid -> !existingIds.contains(uuid))
                .toList();
        if (toEnqueue.isEmpty()) {
            return;
        }

        // Batch check which are already in the queue (single SMISMEMBER per chunk instead of N×SISMEMBER)
        @SuppressWarnings("unchecked")
        byte[] queueSetKeyBytes = ((RedisSerializer<String>) redisTemplate.getKeySerializer()).serialize(REDIS_QUEUE_SET_KEY);
        if (queueSetKeyBytes == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> valueSer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        List<SubmitQueueItem> items = new ArrayList<>();
        List<String> idsToAddToSet = new ArrayList<>();

        for (int chunkStart = 0; chunkStart < toEnqueue.size(); chunkStart += ENQUEUE_CHUNK) {
            int chunkEnd = Math.min(chunkStart + ENQUEUE_CHUNK, toEnqueue.size());
            List<UUID> chunk = toEnqueue.subList(chunkStart, chunkEnd);
            byte[][] memberBytes = new byte[chunk.size()][];
            for (int i = 0; i < chunk.size(); i++) {
                memberBytes[i] = valueSer.serialize(chunk.get(i).toString());
            }
            List<Boolean> inQueue = redisTemplate.execute((RedisConnection connection) ->
                    connection.setCommands().sMIsMember(queueSetKeyBytes, memberBytes));
            if (inQueue == null) {
                inQueue = List.of();
            }
            for (int i = 0; i < chunk.size(); i++) {
                if (Boolean.TRUE.equals(inQueue.get(i))) {
                    continue;
                }
                UUID uuid = chunk.get(i);
                items.add(new SubmitQueueItem(uuid, by));
                idsToAddToSet.add(uuid.toString());
            }
        }
        if (items.isEmpty()) {
            return;
        }

        // Chunked push to list and set to avoid huge single commands
        ListOperations<String, SubmitQueueItem> listOps = submitQueueTemplate.opsForList();
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        for (int i = 0; i < items.size(); i += ENQUEUE_CHUNK) {
            int end = Math.min(i + ENQUEUE_CHUNK, items.size());
            listOps.rightPushAll(REDIS_QUEUE_KEY, items.subList(i, end));
            setOps.add(REDIS_QUEUE_SET_KEY, idsToAddToSet.subList(i, end).toArray());
        }

        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", items.size(), size != null ? size : 0, submittedBy);
    }

    /**
     * Returns the current size of the submission queue (for metrics).
     *
     * @return the number of items in the submit queue, or 0 if unavailable
     */
    public long getSubmissionQueueSize() {
        Long size = submitQueueTemplate.opsForList().size(REDIS_QUEUE_KEY);
        return size != null ? size : 0L;
    }
}
