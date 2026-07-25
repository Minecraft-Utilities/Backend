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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
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
import java.util.concurrent.Semaphore;
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
    private static final int SEEN_CHECK_CHUNK_SIZE = 1000;
    private static final Duration QUEUE_POLL_BLOCK = Duration.ofMillis(250);

    private final boolean enabled;
    private final int maxCandidatesPerPlayer;
    private final int producerPlayerChunkSize;
    private final int producerEnqueueChunkSize;
    private final long maxQueueSize;
    private final int concurrentBulkLookups;
    private final int consumerThreads;
    private final RateLimiter lookupRateLimiter;
    private final Semaphore inflightLookups;
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
            @Value("${mc-utils.username-discovery.lookup-rate-limit:100}") double lookupRateLimit,
            @Value("${mc-utils.username-discovery.concurrent-bulk-lookups:100}") int concurrentBulkLookups,
            @Value("${mc-utils.username-discovery.consumer-threads:4}") int consumerThreads
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
        this.concurrentBulkLookups = Math.max(1, concurrentBulkLookups);
        this.consumerThreads = Math.max(1, consumerThreads);
        this.lookupRateLimiter = RateLimiter.create(lookupRateLimit);
        this.inflightLookups = new Semaphore(this.concurrentBulkLookups);
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
        log.info("Starting username discovery (max {} candidates/player, {} consumer threads, {} concurrent lookups, lookup rate: {}/s)",
                maxCandidatesPerPlayer, this.consumerThreads, this.concurrentBulkLookups, lookupRateLimiter.getRate());
        Main.EXECUTOR.submit(this::runProducerLoop);
        for (int i = 0; i < this.consumerThreads; i++) {
            Main.EXECUTOR.submit(this::runConsumerLoop);
        }
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

        List<UsernameCandidate> pending = new ArrayList<>();
        Set<String> unseen = filterUnseen(unique.keySet());
        for (Map.Entry<String, UsernameCandidate> entry : unique.entrySet()) {
            if (unseen.contains(entry.getKey())) {
                pending.add(entry.getValue());
            }
        }
        long alreadySeen = unique.size() - pending.size();
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
                inflightLookups.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            List<String> chunkPayloads = takeNextChunkPayloads();
            if (chunkPayloads.isEmpty()) {
                inflightLookups.release();
                continue;
            }

            Main.EXECUTOR.submit(() -> {
                try {
                    handleChunk(chunkPayloads);
                } finally {
                    inflightLookups.release();
                }
            });
        }
    }

    private List<String> takeNextChunkPayloads() {
        Optional<String> first = discoveryQueue.blockingPopFirst(QUEUE_POLL_BLOCK);
        if (first.isEmpty()) {
            return List.of();
        }
        List<String> chunk = new ArrayList<>(BULK_LOOKUP_SIZE);
        chunk.add(first.get());
        if (BULK_LOOKUP_SIZE > 1) {
            chunk.addAll(discoveryQueue.popUpTo(BULK_LOOKUP_SIZE - 1));
        }
        return chunk;
    }

    private void handleChunk(List<String> chunkPayloads) {
        List<UsernameCandidate> chunk = chunkPayloads.stream()
                .map(UsernameCandidate::parseQueuePayload)
                .filter(Objects::nonNull)
                .toList();
        if (chunk.isEmpty()) {
            discoveryQueue.removeDedupeKeys(chunkPayloads);
            return;
        }

        ChunkLookupResult result = lookupChunk(chunk, chunkPayloads);
        UsernameDiscoveryMetric metrics = MetricService.getMetric(UsernameDiscoveryMetric.class);
        metrics.recordBulkLookup(result.outcome(), result.durationMs());
        if (result.outcome() != UsernameDiscoveryMetric.BulkLookupOutcome.SUCCESS) {
            discoveryQueue.requeue(result.chunkPayloads());
            return;
        }

        for (UsernameCandidate candidate : result.chunk()) {
            metrics.recordChecked(candidate.strategy(), 1);
        }

        Map<String, UsernameDiscoveryStrategy> strategyByName = new HashMap<>();
        for (UsernameCandidate candidate : result.chunk()) {
            strategyByName.put(candidate.username().toLowerCase(Locale.ROOT), candidate.strategy());
        }

        if (!result.hits().isEmpty()) {
            List<String> uuids = new ArrayList<>(result.hits().size());
            for (MojangUsernameToUuidToken hit : result.hits()) {
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
                    result.hits().size(),
                    result.hits().stream().map(MojangUsernameToUuidToken::username).collect(Collectors.joining(", ")));
        }

        markSeen(result.lookupNames());
        discoveryQueue.removeDedupeKeys(result.chunk().stream().map(UsernameCandidate::username).toList());
    }

    private ChunkLookupResult lookupChunk(List<UsernameCandidate> chunk, List<String> chunkPayloads) {
        List<String> lookupNames = chunk.stream().map(UsernameCandidate::username).toList();
        long lookupStart = System.currentTimeMillis();
        try {
            lookupRateLimiter.acquire();
            List<MojangUsernameToUuidToken> hits = mojangService.bulkLookupNames(lookupNames);
            return new ChunkLookupResult(
                    chunk,
                    chunkPayloads,
                    lookupNames,
                    hits,
                    UsernameDiscoveryMetric.BulkLookupOutcome.SUCCESS,
                    System.currentTimeMillis() - lookupStart
            );
        } catch (RateLimitException e) {
            return new ChunkLookupResult(
                    chunk,
                    chunkPayloads,
                    lookupNames,
                    List.of(),
                    UsernameDiscoveryMetric.BulkLookupOutcome.RATE_LIMITED,
                    System.currentTimeMillis() - lookupStart
            );
        } catch (ResourceAccessException e) {
            log.debug("Timed out during username discovery bulk lookup, re-queuing {} names", chunk.size());
            return new ChunkLookupResult(
                    chunk,
                    chunkPayloads,
                    lookupNames,
                    List.of(),
                    UsernameDiscoveryMetric.BulkLookupOutcome.TIMED_OUT,
                    System.currentTimeMillis() - lookupStart
            );
        } catch (Exception e) {
            log.warn("Username discovery bulk lookup failed, re-queuing {} names", chunk.size(), e);
            return new ChunkLookupResult(
                    chunk,
                    chunkPayloads,
                    lookupNames,
                    List.of(),
                    UsernameDiscoveryMetric.BulkLookupOutcome.ERROR,
                    System.currentTimeMillis() - lookupStart
            );
        }
    }

    private Set<String> filterUnseen(Collection<String> lowercaseUsernames) {
        if (lowercaseUsernames == null || lowercaseUsernames.isEmpty()) {
            return Set.of();
        }
        List<String> names = new ArrayList<>(lowercaseUsernames);
        Set<String> unseen = new LinkedHashSet<>();
        for (int offset = 0; offset < names.size(); offset += SEEN_CHECK_CHUNK_SIZE) {
            int end = Math.min(offset + SEEN_CHECK_CHUNK_SIZE, names.size());
            List<String> chunk = names.subList(offset, end);
            List<Boolean> seen = areSeen(chunk);
            for (int i = 0; i < chunk.size(); i++) {
                if (!Boolean.TRUE.equals(seen.get(i))) {
                    unseen.add(chunk.get(i));
                }
            }
        }
        return unseen;
    }

    @SuppressWarnings("unchecked")
    private List<Boolean> areSeen(List<String> lowercaseUsernames) {
        if (lowercaseUsernames.isEmpty()) {
            return List.of();
        }
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) queueRedis.getKeySerializer();
        RedisSerializer<String> valueSerializer = (RedisSerializer<String>) queueRedis.getValueSerializer();
        byte[] keyBytes = keySerializer.serialize(SEEN_SET_KEY);
        byte[][] memberBytes = new byte[lowercaseUsernames.size()][];
        int index = 0;
        for (String name : lowercaseUsernames) {
            memberBytes[index++] = valueSerializer.serialize(name);
        }
        List<Boolean> result = queueRedis.execute((RedisCallback<List<Boolean>>) connection ->
                connection.setCommands().sMIsMember(keyBytes, memberBytes));
        return result != null ? result : Collections.nCopies(lowercaseUsernames.size(), false);
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

    private record ChunkLookupResult(
            List<UsernameCandidate> chunk,
            List<String> chunkPayloads,
            List<String> lookupNames,
            List<MojangUsernameToUuidToken> hits,
            UsernameDiscoveryMetric.BulkLookupOutcome outcome,
            long durationMs
    ) {}
}
