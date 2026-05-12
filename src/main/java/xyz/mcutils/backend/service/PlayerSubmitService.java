package xyz.mcutils.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.dto.PlayerCreateSubmission;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid} when no submitter).
 */
@Service
@Slf4j
public class PlayerSubmitService {
    
    private static final int BATCH_SIZE = 1_000;
    private static final Semaphore SUBMIT_CONCURRENCY_LIMIT = new Semaphore(10);
    private static final String REDIS_QUEUE_KEY = "player-submit-queue";
    private static final String REDIS_QUEUE_SET_KEY = "player-submit-queue-ids";
    private static final long EMPTY_QUEUE_BLOCK_SECONDS = 2;

    private final RedisTemplate<String, String> redis;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerSubmitService(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis, @Lazy PlayerService playerService, @Lazy MojangService mojangService) {
        this.redis = redis;
        this.playerService = playerService;
        this.mojangService = mojangService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        var listOps = redis.opsForList();
        var setOps = redis.opsForSet();
        Main.EXECUTOR.submit(() -> {
            while (running.get()) {
                try {
                    // Block until at least one item arrives, then drain the rest of the batch.
                    String first = listOps.leftPop(REDIS_QUEUE_KEY, EMPTY_QUEUE_BLOCK_SECONDS, TimeUnit.SECONDS);
                    if (first == null) {
                        continue;
                    }
                    List<String> batch = new ArrayList<>(takeBatchFromQueue(BATCH_SIZE - 1));
                    batch.addFirst(first);
                    processBatch(batch, listOps, setOps);
                } catch (Exception e) {
                    log.error("Submit queue consumer error, continuing", e);
                }
            }
        });
    }

    @SuppressWarnings({"unchecked"})
    private List<String> takeBatchFromQueue(int batchSize) {
        List<Object> results = redis.execute(new SessionCallback<>() {
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
        if (!(first instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object elem : list) {
            if (elem instanceof String s) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Processes a batch of items from the submit queue.
     *
     * @param batch   the list of raw entry strings
     * @param listOps the list operations
     * @param setOps  the set operations
     */
    private void processBatch(List<String> batch, ListOperations<String, String> listOps, SetOperations<String, String> setOps) {
        List<QueueEntry> entries = batch.stream().map(QueueEntry::fromRedisValue).filter(Optional::isPresent).map(Optional::get).toList();
        if (entries.isEmpty()) {
            return;
        }

        // Belt-and-braces: also checked in submitPlayers before enqueue, but entries may have been
        // created before the player existed or the player may have been created via another path.
        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        List<String> duplicateIdsToRemove = entries.stream().filter(e -> existingIds.contains(e.playerId())).map(e -> e.playerId().toString()).toList();
        List<QueueEntry> toProcess = entries.stream().filter(e -> !existingIds.contains(e.playerId())).toList();
        if (!duplicateIdsToRemove.isEmpty()) {
            setOps.remove(REDIS_QUEUE_SET_KEY, (Object[]) duplicateIdsToRemove.toArray(new String[0]));
        }

        Set<String> idsToRemoveFromQueue = ConcurrentHashMap.newKeySet();
        Queue<PlayerCreateSubmission> created = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();
        for (QueueEntry entry : toProcess) {
            Future<?> future = Main.EXECUTOR.submit(() -> {
                try {
                    SUBMIT_CONCURRENCY_LIMIT.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    boolean requeued = false;
                    try {
                        MojangProfileToken token = mojangService.getProfile(entry.playerId().toString());
                        if (token == null) {
                            log.warn("Player with uuid '{}' was not found", entry.playerId());
                            return;
                        }
                        created.add(new PlayerCreateSubmission(token, entry.submittedBy()));
                    } catch (NotFoundException ignored) {
                    } catch (MojangAPIRateLimitException e) {
                        listOps.rightPush(REDIS_QUEUE_KEY, entry.toRedisValue());
                        requeued = true;
                    } finally {
                        if (!requeued) {
                            idsToRemoveFromQueue.add(entry.playerId().toString());
                        }
                    }
                } finally {
                    SUBMIT_CONCURRENCY_LIMIT.release();
                }
            });
            futures.add(future);
        }

        FutureUtils.awaitAll(futures, "submit");
        if (!idsToRemoveFromQueue.isEmpty()) {
            setOps.remove(REDIS_QUEUE_SET_KEY, (Object[]) idsToRemoveFromQueue.toArray(new String[0]));
        }
        playerService.createPlayers(new ArrayList<>(created));
    }

    public int submitPlayers(List<String> players, String submittedBy) {
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;
        List<UUID> toEnqueue = players.stream().filter(id -> id != null && !id.isBlank()).map(id -> UUIDUtils.parseUuid(id.trim())).filter(Objects::nonNull).distinct().toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }
        Set<UUID> existingInDb = playerService.getExistingPlayerIds(toEnqueue);
        toEnqueue = toEnqueue.stream().filter(uuid -> !existingInDb.contains(uuid)).toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }

        List<Boolean> inQueue = isAlreadyQueued(toEnqueue);
        List<String> entryStrings = new ArrayList<>();
        List<String> playerIdStrings = new ArrayList<>();
        for (int i = 0; i < toEnqueue.size(); i++) {
            if (!Boolean.TRUE.equals(inQueue.get(i))) {
                UUID uuid = toEnqueue.get(i);
                entryStrings.add(new QueueEntry(uuid, by).toRedisValue());
                playerIdStrings.add(uuid.toString());
            }
        }
        if (entryStrings.isEmpty()) {
            return 0;
        }

        ListOperations<String, String> listOps = redis.opsForList();
        SetOperations<String, String> setOps = redis.opsForSet();
        listOps.rightPushAll(REDIS_QUEUE_KEY, entryStrings);
        setOps.add(REDIS_QUEUE_SET_KEY, playerIdStrings.toArray(new String[0]));
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", entryStrings.size(), size != null ? size : 0, submittedBy);
        return entryStrings.size();
    }

    /**
     * Checks which of the given UUIDs are already present in the submit queue set.
     * Uses a raw Redis connection to perform an atomic {@code SMISMEMBER} command.
     *
     * @param uuids the UUIDs to check
     * @return a list of booleans parallel to {@code uuids}; {@code true} means already queued
     */
    @SuppressWarnings("unchecked")
    private List<Boolean> isAlreadyQueued(List<UUID> uuids) {
        RedisSerializer<String> keySer = (RedisSerializer<String>) redis.getKeySerializer();
        RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        byte[] keyBytes = keySer.serialize(REDIS_QUEUE_SET_KEY);
        byte[][] memberBytes = new byte[uuids.size()][];
        for (int i = 0; i < uuids.size(); i++) {
            memberBytes[i] = valueSer.serialize(uuids.get(i).toString());
        }
        List<Boolean> result = redis.execute((RedisConnection connection) -> connection.setCommands().sMIsMember(keyBytes, memberBytes));
        return result != null ? result : List.of();
    }

    public long getSubmissionQueueSize() {
        Long size = redis.opsForList().size(REDIS_QUEUE_KEY);
        return size != null ? size : 0L;
    }


    public void stop() {
        running.set(false);
    }

    private record QueueEntry(UUID playerId, UUID submittedBy) {
        String toRedisValue() {
            return submittedBy != null
                    ? playerId + "," + submittedBy
                    : playerId.toString();
        }

        static Optional<QueueEntry> fromRedisValue(String s) {
            if (s == null || s.isBlank()) {
                return Optional.empty();
            }
            String[] parts = s.split(",", 2);
            UUID playerId = UUIDUtils.parseUuid(parts[0].trim());
            if (playerId == null) {
                return Optional.empty();
            }
            UUID submittedBy = parts.length > 1 && !parts[1].isBlank()
                    ? UUIDUtils.parseUuid(parts[1].trim())
                    : null;
            return Optional.of(new QueueEntry(playerId, submittedBy));
        }
    }
}
