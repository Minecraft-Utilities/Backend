package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitOutcomesMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerSubmitProcessingMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated submit queue for tracking new players.
 * Queue entries are stored as strings: {@code playerUuid,submitterUuid} (or {@code playerUuid} when no submitter).
 */
@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class PlayerSubmitService {

    private static final int BATCH_SIZE = 1000;
    private static final int RATE_LIMIT = 500;
    private static final String QUEUE_NAME = "player-submit-queue";
    private static final Duration EMPTY_QUEUE_BLOCK = Duration.ofSeconds(2);

    private final RateLimiter rateLimiter = RateLimiter.create(RATE_LIMIT);
    private final RedisQueue submitQueue;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PlayerSubmitService(RedisQueueFactory queueFactory, @Lazy PlayerService playerService, @Lazy MojangService mojangService) {
        this.submitQueue = queueFactory.getQueue(QUEUE_NAME);
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
        while (running.get()) {
            try {
                Optional<List<String>> batch = submitQueue.blockingTakeBatch(BATCH_SIZE, EMPTY_QUEUE_BLOCK);
                if (batch.isEmpty()) {
                    continue;
                }
                processBatch(batch.get());
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.error("Submit queue consumer error, continuing", e);
            }
        }
    }

    private void processBatch(List<String> batch) {
        List<QueueEntry> entries = parseEntries(batch);
        if (entries.isEmpty()) {
            return;
        }
        List<QueueEntry> toProcess = filterExisting(entries);
        Map<UUID, Long> submitterCounts = processEntries(toProcess);
        submitterCounts.forEach(playerService::incrementSubmittedUuids);
    }

    private List<QueueEntry> parseEntries(List<String> batch) {
        return batch.stream()
                .map(QueueEntry::fromRedisValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private List<QueueEntry> filterExisting(List<QueueEntry> entries) {
        Set<UUID> existingIds = playerService.getExistingPlayerIds(entries.stream().map(QueueEntry::playerId).toList());
        if (!existingIds.isEmpty()) {
            List<String> duplicateIds = entries.stream()
                    .filter(e -> existingIds.contains(e.playerId()))
                    .map(e -> e.playerId().toString())
                    .toList();
            submitQueue.removeDedupeKeys(duplicateIds);
        }
        return entries.stream().filter(e -> !existingIds.contains(e.playerId())).toList();
    }

    private Map<UUID, Long> processEntries(List<QueueEntry> toProcess) {
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
        List<String> toRequeue = new ArrayList<>();

        for (FetchResult result : fetchResults) {
            if (result.outcome() == PlayerSubmitProcessingMetric.Outcome.RATE_LIMITED
                    || result.outcome() == PlayerSubmitProcessingMetric.Outcome.TIMED_OUT) {
                toRequeue.add(result.entry().toRedisValue());
            } else {
                idsToRemoveFromQueue.add(result.entry().playerId().toString());
                if (result.token() != null) {
                    tokensToSave.add(result.token());
                    successResults.add(result);
                }
            }
        }

        if (!toRequeue.isEmpty()) {
            submitQueue.requeue(toRequeue);
        }

        if (!tokensToSave.isEmpty()) {
            playerService.createPlayers(tokensToSave);
            for (FetchResult result : successResults) {
                if (result.entry().submittedBy() != null) {
                    submitterCounts.merge(result.entry().submittedBy(), 1L, Long::sum);
                }
            }
        }

        submitQueue.removeDedupeKeys(idsToRemoveFromQueue);
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

        List<RedisQueue.QueueItem> items = toEnqueue.stream()
                .map(uuid -> new RedisQueue.QueueItem(new QueueEntry(uuid, by).toRedisValue(), uuid.toString()))
                .toList();
        RedisQueue.EnqueueResult result = submitQueue.enqueue(items);
        if (result.alreadyQueued() > 0) {
            MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ALREADY_QUEUED, result.alreadyQueued());
        }
        if (result.enqueued() == 0) {
            return 0;
        }

        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})",
                result.enqueued(), submitQueue.size(), submittedBy);
        MetricService.getMetric(PlayerSubmitOutcomesMetric.class).inc(PlayerSubmitOutcomesMetric.Outcome.ENQUEUED, result.enqueued());
        return result.enqueued();
    }

    public long getSubmissionQueueSize() {
        return submitQueue.size();
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
