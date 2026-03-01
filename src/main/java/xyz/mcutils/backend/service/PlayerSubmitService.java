package xyz.mcutils.backend.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
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
import java.util.concurrent.*;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid,} when no submitter).
 */
@Service
@Slf4j
public class PlayerSubmitService {
    private static final String REDIS_QUEUE_KEY = "player-submit-queue";
    private static final String REDIS_QUEUE_SET_KEY = "player-submit-queue-ids";
    private static final int BATCH_SIZE = 1_000;
    private static final long EMPTY_QUEUE_BLOCK_SECONDS = 2;
    private final RedisTemplate<String, String> redis;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final Semaphore submitConcurrencyLimit = new Semaphore(50);

    private volatile boolean running = true;

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
        var listOps = redis.opsForList();
        var setOps = redis.opsForSet();
        Main.EXECUTOR.submit(() -> {
            while (running) {
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
     * @param batch the list of raw entry strings
     * @param listOps the list operations
     * @param setOps the set operations
     */
    @SneakyThrows
    private void processBatch(List<String> batch, ListOperations<String, String> listOps, SetOperations<String, String> setOps) {
        List<QueueEntry> entries = batch.stream().map(this::parseEntry).filter(Optional::isPresent).map(Optional::get).toList();
        if (entries.isEmpty()) {
            return;
        }

        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        List<String> duplicateIdsToRemove = entries.stream().filter(e -> existingIds.contains(e.playerId())).map(e -> e.playerId().toString()).toList();
        List<QueueEntry> toProcess = entries.stream().filter(e -> !existingIds.contains(e.playerId())).toList();
        if (!duplicateIdsToRemove.isEmpty()) {
            setOps.remove(REDIS_QUEUE_SET_KEY, duplicateIdsToRemove.toArray());
        }

        Set<String> idsToRemoveFromQueue = ConcurrentHashMap.newKeySet();
        List<PlayerCreateSubmission> created = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (QueueEntry entry : toProcess) {
            String raw = entry.playerId() + "," + (entry.submittedBy() != null ? entry.submittedBy() : "");
            Future<?> future = Main.EXECUTOR.submit(() -> {
                submitConcurrencyLimit.acquireUninterruptibly();
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
                        listOps.rightPush(REDIS_QUEUE_KEY, raw);
                        requeued = true;
                    } finally {
                        if (!requeued) {
                            idsToRemoveFromQueue.add(entry.playerId().toString());
                        }
                    }
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
        if (!idsToRemoveFromQueue.isEmpty()) {
            setOps.remove(REDIS_QUEUE_SET_KEY, idsToRemoveFromQueue.toArray());
        }
        playerService.createPlayers(created);
    }

    public int submitPlayers(List<String> players, String submittedBy) {
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;
        List<UUID> toEnqueue = players.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> UUIDUtils.parseUuid(id.trim()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }
        Set<UUID> existingInDb = playerService.getExistingPlayerIds(toEnqueue);
        toEnqueue = toEnqueue.stream().filter(uuid -> !existingInDb.contains(uuid)).toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }

        @SuppressWarnings("unchecked") RedisSerializer<String> keySer = (RedisSerializer<String>) redis.getKeySerializer();
        @SuppressWarnings("unchecked") RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        byte[] keyBytes = keySer.serialize(REDIS_QUEUE_SET_KEY);
        List<String> entryStrings = new ArrayList<>();
        List<String> playerIdStrings = new ArrayList<>();
        byte[][] memberBytes = new byte[toEnqueue.size()][];
        for (int i = 0; i < toEnqueue.size(); i++) {
            memberBytes[i] = valueSer.serialize(toEnqueue.get(i).toString());
        }
        List<Boolean> inQueue = redis.execute((RedisConnection connection) -> connection.setCommands().sMIsMember(keyBytes, memberBytes));
        if (inQueue == null) {
            inQueue = List.of();
        }
        for (int i = 0; i < toEnqueue.size(); i++) {
            if (!Boolean.TRUE.equals(inQueue.get(i))) {
                UUID uuid = toEnqueue.get(i);
                entryStrings.add(uuid + "," + (by != null ? by : ""));
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

    public long getSubmissionQueueSize() {
        Long size = redis.opsForList().size(REDIS_QUEUE_KEY);
        return size != null ? size : 0L;
    }

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

    public void stop() {
        running = false;
    }

    private record QueueEntry(UUID playerId, UUID submittedBy) { }
}
