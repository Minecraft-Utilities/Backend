package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitOutcomesMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitProcessingMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid} when no submitter).
 */
@Service
@Slf4j
public class PlayerSubmitService {

    private static final int BATCH_SIZE = 1_000;
    private static final String REDIS_QUEUE_KEY = "player-submit-queue";
    private static final String REDIS_QUEUE_SET_KEY = "player-submit-queue-ids";
    private static final long EMPTY_QUEUE_BLOCK_SECONDS = 2;
    private static final int RATE_LIMIT = 250;

    private final RateLimiter rateLimiter = RateLimiter.create(RATE_LIMIT);
    private final RedisTemplate<String, String> redis;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerSubmitService(
            @Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis,
            @Lazy PlayerService playerService,
            @Lazy MojangService mojangService) {
        this.redis = redis;
        this.playerService = playerService;
        this.mojangService = mojangService;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        Main.EXECUTOR.submit(this::runConsumerLoop);
    }

    private void runConsumerLoop() {
        ListOperations<String, String> listOps = redis.opsForList();
        SetOperations<String, String> setOps = redis.opsForSet();
        while (running.get()) {
            try {
                String first = listOps.leftPop(REDIS_QUEUE_KEY, EMPTY_QUEUE_BLOCK_SECONDS, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                List<String> batch = new ArrayList<>(BATCH_SIZE);
                batch.add(first);
                batch.addAll(takeBatchFromQueue(BATCH_SIZE - 1));
                processBatch(batch, listOps, setOps);
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.error("Submit queue consumer error, continuing", e);
            }
        }
    }

    private List<String> takeBatchFromQueue(int batchSize) {
        List<String> result = redis.opsForList().leftPop(REDIS_QUEUE_KEY, batchSize);
        return result != null ? result : List.of();
    }

    private void processBatch(List<String> batch, ListOperations<String, String> listOps, SetOperations<String, String> setOps) {
        List<QueueEntry> entries = parseEntries(batch);
        if (entries.isEmpty()) {
            return;
        }
        List<QueueEntry> toProcess = filterExisting(entries, setOps);
        Map<UUID, Long> submitterCounts = processEntries(toProcess, listOps, setOps);
        submitterCounts.forEach(playerService::incrementSubmittedUuids);
    }

    private List<QueueEntry> parseEntries(List<String> batch) {
        return batch.stream()
                .map(QueueEntry::fromRedisValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private List<QueueEntry> filterExisting(List<QueueEntry> entries, SetOperations<String, String> setOps) {
        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        if (!existingIds.isEmpty()) {
            List<String> duplicateIds = entries.stream()
                    .filter(e -> existingIds.contains(e.playerId()))
                    .map(e -> e.playerId().toString())
                    .toList();
            removeFromQueueSet(setOps, duplicateIds);
        }
        return entries.stream().filter(e -> !existingIds.contains(e.playerId())).toList();
    }

    private Map<UUID, Long> processEntries(List<QueueEntry> toProcess, ListOperations<String, String> listOps, SetOperations<String, String> setOps) {
        Queue<FetchResult> fetchResults = new ConcurrentLinkedQueue<>();
        List<Future<Void>> futures = new ArrayList<>();

        for (QueueEntry entry : toProcess) {
            rateLimiter.acquire();
            futures.add(Main.EXECUTOR.submit(() -> {
                fetchResults.add(fetchProfile(entry));
                return null;
            }));
        }

        FutureUtils.awaitAll(futures, "submit");

        Set<String> idsToRemoveFromQueue = new HashSet<>();
        Map<UUID, Long> submitterCounts = new HashMap<>();
        List<MojangProfileToken> tokensToSave = new ArrayList<>();
        List<FetchResult> successResults = new ArrayList<>();

        for (FetchResult result : fetchResults) {
            if (result.outcome() == PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED
                    || result.outcome() == PlayerSubmitProcessingMetric.Outcome.TIMED_OUT) {
                listOps.rightPush(REDIS_QUEUE_KEY, result.entry().toRedisValue());
            } else {
                idsToRemoveFromQueue.add(result.entry().playerId().toString());
                if (result.token() != null) {
                    tokensToSave.add(result.token());
                    successResults.add(result);
                }
            }
        }

        if (!tokensToSave.isEmpty()) {
            playerService.savePlayers(tokensToSave);
            for (FetchResult result : successResults) {
                if (result.entry().submittedBy() != null) {
                    submitterCounts.merge(result.entry().submittedBy(), 1L, Long::sum);
                }
            }
        }

        removeFromQueueSet(setOps, idsToRemoveFromQueue);
        return submitterCounts;
    }

    private FetchResult fetchProfile(QueueEntry entry) {
        long processStart = System.currentTimeMillis();
        try {
            MojangProfileToken token = mojangService.getProfile(entry.playerId().toString());
            if (token == null) {
                log.warn("Player with uuid '{}' was not found", entry.playerId());
                recordOutcome(PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
                return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
            }
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.CREATED, processStart);
            return new FetchResult(entry, token, PlayerSubmitProcessingMetric.Outcome.CREATED, processStart);
        } catch (NotFoundException e) {
            log.debug("Player {} not found on Mojang, removing from queue", entry.playerId(), e);
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.NOT_FOUND, processStart);
        } catch (MojangAPIRateLimitException e) {
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED, processStart);
        } catch (ResourceAccessException e) {
            log.debug("Timed out fetching profile for {}, re-queuing", entry.playerId());
            recordOutcome(PlayerSubmitProcessingMetric.Outcome.TIMED_OUT, processStart);
            return new FetchResult(entry, null, PlayerSubmitProcessingMetric.Outcome.TIMED_OUT, processStart);
        }
    }

    private void recordOutcome(PlayerSubmitProcessingMetric.Outcome outcome, long startMs) {
        MetricService.getMetric(PlayerSubmitProcessingMetric.class).record(outcome, System.currentTimeMillis() - startMs);
    }

    private void removeFromQueueSet(SetOperations<String, String> setOps, Collection<String> ids) {
        if (!ids.isEmpty()) {
            setOps.remove(REDIS_QUEUE_SET_KEY, ids.toArray(new String[0]));
        }
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
        if (!existingInDb.isEmpty()) {
            MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ALREADY_TRACKED, existingInDb.size());
        }
        toEnqueue = toEnqueue.stream().filter(uuid -> !existingInDb.contains(uuid)).toList();
        if (toEnqueue.isEmpty()) {
            return 0;
        }

        List<Boolean> inQueue = isAlreadyQueued(toEnqueue);
        List<String> entryStrings = new ArrayList<>();
        List<String> playerIdStrings = new ArrayList<>();
        long alreadyQueuedCount = 0;
        for (int i = 0; i < toEnqueue.size(); i++) {
            if (!Boolean.TRUE.equals(inQueue.get(i))) {
                UUID uuid = toEnqueue.get(i);
                entryStrings.add(new QueueEntry(uuid, by).toRedisValue());
                playerIdStrings.add(uuid.toString());
            } else {
                alreadyQueuedCount++;
            }
        }
        if (alreadyQueuedCount > 0) {
            MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ALREADY_QUEUED, alreadyQueuedCount);
        }
        if (entryStrings.isEmpty()) {
            return 0;
        }

        ListOperations<String, String> listOps = redis.opsForList();
        SetOperations<String, String> setOps = redis.opsForSet();
        listOps.rightPushAll(REDIS_QUEUE_KEY, entryStrings);
        setOps.add(REDIS_QUEUE_SET_KEY, playerIdStrings.toArray(new String[0]));
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})",
                entryStrings.size(), size != null ? size : 0, submittedBy);
        MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ENQUEUED, entryStrings.size());
        return entryStrings.size();
    }

    @SuppressWarnings("unchecked")
    private List<Boolean> isAlreadyQueued(List<UUID> uuids) {
        RedisSerializer<String> keySer = (RedisSerializer<String>) redis.getKeySerializer();
        RedisSerializer<String> valueSer = (RedisSerializer<String>) redis.getValueSerializer();
        byte[] keyBytes = keySer.serialize(REDIS_QUEUE_SET_KEY);
        byte[][] memberBytes = new byte[uuids.size()][];
        for (int i = 0; i < uuids.size(); i++) {
            memberBytes[i] = valueSer.serialize(uuids.get(i).toString());
        }
        List<Boolean> result = redis.execute((RedisConnection connection) ->
                connection.setCommands().sMIsMember(keyBytes, memberBytes));
        return result != null ? result : List.of();
    }

    public long getSubmissionQueueSize() {
        Long size = redis.opsForList().size(REDIS_QUEUE_KEY);
        return size != null ? size : 0L;
    }

    private record FetchResult(QueueEntry entry, MojangProfileToken token, PlayerSubmitProcessingMetric.Outcome outcome, long processStart) {}

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