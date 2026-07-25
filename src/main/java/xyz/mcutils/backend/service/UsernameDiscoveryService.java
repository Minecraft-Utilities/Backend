package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.discovery.UsernameCandidate;
import xyz.mcutils.backend.discovery.UsernameDiscoveryConfig;
import xyz.mcutils.backend.discovery.UsernameDiscoveryEngine;
import xyz.mcutils.backend.discovery.UsernameDiscoveryStrategy;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.metric.impl.player.UsernameDiscoveryMetric;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Discovers new players by generating username candidates from tracked players,
 * resolving them via Mojang bulk lookup, and enqueueing hits for ingestion.
 */
@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class UsernameDiscoveryService {
    private static final String QUEUE_NAME = "username-discovery-queue";
    private static final String SEEN_SET_KEY = "username-discovery-seen";
    private static final String CURSOR_KEY = "username-discovery:player-cursor";
    private static final UUID MIN_CURSOR = new UUID(0L, 0L);
    private static final int BULK_LOOKUP_SIZE = 10;
    private static final int CONSUMER_BATCH_SIZE = 500;
    private static final int SEEN_CHECK_CHUNK_SIZE = 1000;
    private static final Duration EMPTY_QUEUE_BLOCK = Duration.ofSeconds(2);

    private final boolean enabled;
    private final int maxCandidatesPerPlayer;
    private final int producerPlayerChunkSize;
    private final int producerEnqueueChunkSize;
    private final long maxQueueSize;
    private final RateLimiter lookupRateLimiter;
    private final UsernameDiscoveryEngine discoveryEngine;

    private final RedisQueue discoveryQueue;
    private final RedisTemplate<String, String> queueRedis;
    private final PlayerRepository playerRepository;
    private final MojangService mojangService;
    private final PlayerSubmitService playerSubmitService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public UsernameDiscoveryService(
            RedisQueueFactory queueFactory,
            @Qualifier("queueRedisTemplate") RedisTemplate<String, String> queueRedis,
            PlayerRepository playerRepository,
            MojangService mojangService,
            @Lazy PlayerSubmitService playerSubmitService,
            @Value("${mc-utils.username-discovery.enabled:false}") boolean enabled,
            @Value("${mc-utils.username-discovery.max-candidates-per-player:750}") int maxCandidatesPerPlayer,
            @Value("${mc-utils.username-discovery.numeric-suffix-max:99}") int numericSuffixMax,
            @Value("${mc-utils.username-discovery.numeric-suffix-extended-max:999}") int numericSuffixExtendedMax,
            @Value("${mc-utils.username-discovery.max-leet-variants-per-base:16}") int maxLeetVariantsPerBase,
            @Value("${mc-utils.username-discovery.max-omit-variants-per-base:12}") int maxOmitVariantsPerBase,
            @Value("#{'${mc-utils.username-discovery.suffixes}'.split(',')}") List<String> suffixes,
            @Value("#{'${mc-utils.username-discovery.prefixes}'.split(',')}") List<String> prefixes,
            @Value("#{'${mc-utils.username-discovery.affixes}'.split(',')}") List<String> affixes,
            @Value("#{'${mc-utils.username-discovery.birth-years}'.split(',')}") List<Integer> birthYears,
            @Value("${mc-utils.username-discovery.producer-player-chunk-size:250}") int producerPlayerChunkSize,
            @Value("${mc-utils.username-discovery.producer-enqueue-chunk-size:2000}") int producerEnqueueChunkSize,
            @Value("${mc-utils.username-discovery.max-queue-size:250000}") long maxQueueSize,
            @Value("${mc-utils.username-discovery.lookup-rate-limit:50}") double lookupRateLimit
    ) {
        this.discoveryQueue = queueFactory.getQueue(QUEUE_NAME);
        this.queueRedis = queueRedis;
        this.playerRepository = playerRepository;
        this.mojangService = mojangService;
        this.playerSubmitService = playerSubmitService;
        this.enabled = enabled;
        this.maxCandidatesPerPlayer = maxCandidatesPerPlayer;
        this.producerPlayerChunkSize = producerPlayerChunkSize;
        this.producerEnqueueChunkSize = producerEnqueueChunkSize;
        this.maxQueueSize = maxQueueSize;
        this.lookupRateLimiter = RateLimiter.create(lookupRateLimit);
        this.discoveryEngine = new UsernameDiscoveryEngine(new UsernameDiscoveryConfig(
                maxCandidatesPerPlayer,
                numericSuffixMax,
                numericSuffixExtendedMax,
                maxLeetVariantsPerBase,
                maxOmitVariantsPerBase,
                cleanList(suffixes, UsernameDiscoveryConfig.DEFAULT_SUFFIXES),
                cleanList(prefixes, UsernameDiscoveryConfig.DEFAULT_PREFIXES),
                cleanList(affixes, UsernameDiscoveryConfig.DEFAULT_AFFIXES),
                birthYears == null || birthYears.isEmpty() ? UsernameDiscoveryConfig.DEFAULT_BIRTH_YEARS : birthYears
        ));
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        if (!enabled) {
            log.info("Username discovery is disabled");
            return;
        }
        log.info("Starting username discovery (max {} candidates/player, lookup rate: {}/s)",
                maxCandidatesPerPlayer, lookupRateLimiter.getRate());
        Main.EXECUTOR.submit(this::runProducerLoop);
        Main.EXECUTOR.submit(this::runConsumerLoop);
    }

    public long getQueueSize() {
        return discoveryQueue.size();
    }

    public long getSeenSetSize() {
        Long size = queueRedis.opsForSet().size(SEEN_SET_KEY);
        return size != null ? size : 0L;
    }

    private void runProducerLoop() {
        while (running.get()) {
            long producerStart = System.currentTimeMillis();
            try {
                if (discoveryQueue.size() >= maxQueueSize) {
                    Thread.sleep(Duration.ofSeconds(5));
                    continue;
                }

                UUID cursor = readCursor();
                List<PlayerRow> players = playerRepository.findPlayersAfter(cursor, PageRequest.of(0, producerPlayerChunkSize));
                if (players.isEmpty()) {
                    writeCursor(MIN_CURSOR);
                    MetricService.getMetric(UsernameDiscoveryMetric.class).recordProducerCycle();
                    Thread.sleep(Duration.ofMinutes(1));
                    continue;
                }

                List<UsernameCandidate> candidates = new ArrayList<>();
                for (PlayerRow player : players) {
                    List<UsernameCandidate> generated = discoveryEngine.generateCandidates(player.getUsername());
                    candidates.addAll(generated);
                    Map<UsernameDiscoveryStrategy, Long> byStrategy = generated.stream()
                            .collect(Collectors.groupingBy(UsernameCandidate::strategy, Collectors.counting()));
                    UsernameDiscoveryMetric metrics = MetricService.getMetric(UsernameDiscoveryMetric.class);
                    byStrategy.forEach((strategy, count) -> metrics.recordGenerated(strategy, count));
                }

                int enqueued = enqueueCandidates(candidates);
                MetricService.getMetric(UsernameDiscoveryMetric.class).recordProducerPlayersScanned(players.size());
                MetricService.getMetric(UsernameDiscoveryMetric.class).recordProducerDuration(System.currentTimeMillis() - producerStart);
                writeCursor(players.get(players.size() - 1).getId());

                if (enqueued > 0) {
                    log.debug("Username discovery enqueued {} candidates from {} players (queue size: {})",
                            enqueued, players.size(), discoveryQueue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.error("Username discovery producer error, retrying in 5s", e);
                sleepQuietly(Duration.ofSeconds(5));
            }
        }
    }

    private int enqueueCandidates(List<UsernameCandidate> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }

        UsernameDiscoveryMetric metrics = MetricService.getMetric(UsernameDiscoveryMetric.class);
        Map<String, UsernameCandidate> unique = new LinkedHashMap<>();
        long duplicateInBatch = 0;
        long invalid = 0;

        for (UsernameCandidate candidate : candidates) {
            if (!UsernameDiscoveryEngine.isValidCandidate(candidate.username())) {
                invalid++;
                continue;
            }
            String key = candidate.username().toLowerCase(Locale.ROOT);
            if (unique.containsKey(key)) {
                duplicateInBatch++;
                continue;
            }
            unique.put(key, candidate);
        }

        metrics.recordSkipped(UsernameDiscoveryMetric.SkipReason.INVALID, invalid);
        metrics.recordSkipped(UsernameDiscoveryMetric.SkipReason.DUPLICATE_IN_BATCH, duplicateInBatch);

        List<UsernameCandidate> pending = new ArrayList<>(unique.size());
        long alreadySeen = 0;
        for (Map.Entry<String, UsernameCandidate> entry : unique.entrySet()) {
            if (isSeen(entry.getKey())) {
                alreadySeen++;
            } else {
                pending.add(entry.getValue());
            }
        }
        metrics.recordSkipped(UsernameDiscoveryMetric.SkipReason.ALREADY_SEEN, alreadySeen);
        if (pending.isEmpty()) {
            return 0;
        }

        int enqueued = 0;
        for (int offset = 0; offset < pending.size(); offset += producerEnqueueChunkSize) {
            if (!running.get() || discoveryQueue.size() >= maxQueueSize) {
                break;
            }
            int end = Math.min(offset + producerEnqueueChunkSize, pending.size());
            List<UsernameCandidate> chunk = pending.subList(offset, end);

            Set<String> usernames = chunk.stream().map(UsernameCandidate::username).collect(Collectors.toSet());
            Set<String> existing = playerRepository.findExistingUsernamesLower(usernames);
            if (!existing.isEmpty()) {
                metrics.recordSkipped(UsernameDiscoveryMetric.SkipReason.ALREADY_TRACKED, existing.size());
            }

            List<RedisQueue.QueueItem> items = new ArrayList<>();
            Map<UsernameDiscoveryStrategy, Long> enqueuedByStrategy = new EnumMap<>(UsernameDiscoveryStrategy.class);
            for (UsernameCandidate candidate : chunk) {
                if (existing.contains(candidate.username())) {
                    continue;
                }
                items.add(new RedisQueue.QueueItem(candidate.queuePayload(), candidate.username()));
                enqueuedByStrategy.merge(candidate.strategy(), 1L, Long::sum);
            }
            if (items.isEmpty()) {
                continue;
            }

            RedisQueue.EnqueueResult result = discoveryQueue.enqueue(items);
            enqueued += result.enqueued();
            if (result.alreadyQueued() > 0) {
                metrics.recordSkipped(UsernameDiscoveryMetric.SkipReason.ALREADY_SEEN, result.alreadyQueued());
            }
            enqueuedByStrategy.forEach(metrics::recordEnqueued);
        }
        return enqueued;
    }

    private void runConsumerLoop() {
        while (running.get()) {
            try {
                Optional<List<String>> batch = discoveryQueue.blockingTakeBatch(CONSUMER_BATCH_SIZE, EMPTY_QUEUE_BLOCK);
                if (batch.isEmpty()) {
                    continue;
                }
                processBatch(batch.get());
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.error("Username discovery consumer error, continuing", e);
            }
        }
    }

    private void processBatch(List<String> payloads) {
        List<String> toRequeue = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        UsernameDiscoveryMetric metrics = MetricService.getMetric(UsernameDiscoveryMetric.class);

        for (int offset = 0; offset < payloads.size(); offset += BULK_LOOKUP_SIZE) {
            if (!running.get()) {
                toRequeue.addAll(payloads.subList(offset, payloads.size()));
                break;
            }

            int end = Math.min(offset + BULK_LOOKUP_SIZE, payloads.size());
            List<String> chunkPayloads = payloads.subList(offset, end);
            List<UsernameCandidate> chunk = chunkPayloads.stream()
                    .map(UsernameCandidate::parseQueuePayload)
                    .filter(Objects::nonNull)
                    .toList();
            if (chunk.isEmpty()) {
                completed.addAll(chunkPayloads);
                continue;
            }

            List<String> lookupNames = chunk.stream().map(UsernameCandidate::username).toList();
            long lookupStart = System.currentTimeMillis();
            try {
                lookupRateLimiter.acquire();
                List<MojangUsernameToUuidToken> hits = mojangService.bulkLookupNames(lookupNames);
                metrics.recordBulkLookup(UsernameDiscoveryMetric.BulkLookupOutcome.SUCCESS, System.currentTimeMillis() - lookupStart);

                for (UsernameCandidate candidate : chunk) {
                    metrics.recordChecked(candidate.strategy(), 1);
                }

                Map<String, UsernameDiscoveryStrategy> strategyByName = new HashMap<>();
                for (UsernameCandidate candidate : chunk) {
                    strategyByName.put(candidate.username().toLowerCase(Locale.ROOT), candidate.strategy());
                }

                if (!hits.isEmpty()) {
                    List<String> uuids = new ArrayList<>(hits.size());
                    for (MojangUsernameToUuidToken hit : hits) {
                        uuids.add(hit.uuid());
                        UsernameDiscoveryStrategy strategy = strategyByName.getOrDefault(
                                hit.username().toLowerCase(Locale.ROOT),
                                null
                        );
                        metrics.recordHit(strategy);
                    }
                    int submitted = playerSubmitService.submitPlayers(uuids, null);
                    metrics.recordSubmitEnqueued(submitted);
                    log.info("Username discovery found {} profile(s): {}",
                            hits.size(),
                            hits.stream().map(MojangUsernameToUuidToken::username).collect(Collectors.joining(", ")));
                }

                markSeen(lookupNames);
                completed.addAll(chunkPayloads);
            } catch (RateLimitException e) {
                metrics.recordBulkLookup(UsernameDiscoveryMetric.BulkLookupOutcome.RATE_LIMITED, System.currentTimeMillis() - lookupStart);
                toRequeue.addAll(chunkPayloads);
            } catch (ResourceAccessException e) {
                metrics.recordBulkLookup(UsernameDiscoveryMetric.BulkLookupOutcome.TIMED_OUT, System.currentTimeMillis() - lookupStart);
                log.debug("Timed out during username discovery bulk lookup, re-queuing {} names", chunk.size());
                toRequeue.addAll(chunkPayloads);
            } catch (Exception e) {
                metrics.recordBulkLookup(UsernameDiscoveryMetric.BulkLookupOutcome.ERROR, System.currentTimeMillis() - lookupStart);
                log.warn("Username discovery bulk lookup failed, re-queuing {} names", chunk.size(), e);
                toRequeue.addAll(chunkPayloads);
            }
        }

        if (!toRequeue.isEmpty()) {
            discoveryQueue.requeue(toRequeue);
        }
        if (!completed.isEmpty()) {
            discoveryQueue.removeDedupeKeys(completed.stream()
                    .map(UsernameCandidate::parseQueuePayload)
                    .filter(Objects::nonNull)
                    .map(UsernameCandidate::username)
                    .toList());
        }
    }

    private boolean isSeen(String lowercaseUsername) {
        Boolean member = queueRedis.opsForSet().isMember(SEEN_SET_KEY, lowercaseUsername);
        return Boolean.TRUE.equals(member);
    }

    private void markSeen(Collection<String> lowercaseUsernames) {
        if (lowercaseUsernames == null || lowercaseUsernames.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < lowercaseUsernames.size(); offset += SEEN_CHECK_CHUNK_SIZE) {
            int end = Math.min(offset + SEEN_CHECK_CHUNK_SIZE, lowercaseUsernames.size());
            List<String> chunk = new ArrayList<>(lowercaseUsernames).subList(offset, end);
            queueRedis.opsForSet().add(SEEN_SET_KEY, chunk.toArray(new String[0]));
        }
    }

    private UUID readCursor() {
        String value = queueRedis.opsForValue().get(CURSOR_KEY);
        if (value == null || value.isBlank()) {
            return MIN_CURSOR;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid username discovery cursor '{}', resetting", value);
            return MIN_CURSOR;
        }
    }

    private void writeCursor(UUID cursor) {
        queueRedis.opsForValue().set(CURSOR_KEY, cursor.toString());
    }

    private static List<String> cleanList(List<String> values, List<String> defaults) {
        if (values == null || values.isEmpty()) {
            return defaults;
        }
        List<String> cleaned = values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        return cleaned.isEmpty() ? defaults : cleaned;
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
