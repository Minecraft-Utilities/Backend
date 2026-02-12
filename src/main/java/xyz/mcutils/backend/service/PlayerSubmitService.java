package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;
import org.xbill.DNS.tools.primary;

import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.redis.SubmitQueueItem;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated submit queue for tracking new players.
 */
@SuppressWarnings("UnstableApiUsage")
@Service
@Log4j2
public class PlayerSubmitService {
    private static final String REDIS_QUEUE_KEY = "player-submit-queue";
    private static final String REDIS_QUEUE_SET_KEY = "player-submit-queue-ids";
    private static final int SUBMIT_RATE_PER_SECOND = 300;
    private static final int SUBMIT_WORKER_THREADS = 250;

    private static final RateLimiter submitRateLimiter = RateLimiter.create(SUBMIT_RATE_PER_SECOND);
    private static final ExecutorService submitWorkers = Executors.newFixedThreadPool(SUBMIT_WORKER_THREADS);

    private final RedisTemplate<String, SubmitQueueItem> submitQueueTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public PlayerSubmitService(RedisTemplate<String, SubmitQueueItem> submitQueueTemplate, RedisTemplate<String, Object> redisTemplate,
                               @Lazy PlayerService playerService, @Lazy MojangService mojangService, MongoTemplate mongoTemplate) {
        this.submitQueueTemplate = submitQueueTemplate;
        this.redisTemplate = redisTemplate;
        this.playerService = playerService;
        this.mojangService = mojangService;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        ListOperations<String, SubmitQueueItem> listOps = submitQueueTemplate.opsForList();
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        Main.EXECUTOR.submit(() -> {
            while (true) {
                List<SubmitQueueItem> batch = listOps.range(REDIS_QUEUE_KEY, 0, SUBMIT_RATE_PER_SECOND);
                if (batch.isEmpty()) {
                    continue;
                }

                // Acquire all permits upfront
                submitRateLimiter.acquire(batch.size());
                
                // Submit all work
                batch.forEach(item -> submitWorkers.submit(() -> processItem(item, listOps, setOps)));
            }
        });
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
        try {
            if (this.playerService.exists(id)) {
                setOps.remove(REDIS_QUEUE_SET_KEY, id.toString());
                return;
            }
            MojangProfileToken token = this.mojangService.getProfile(id.toString());
                if (token == null) {
                    log.warn("Player with uuid '{}' was not found", id);
                    setOps.remove(REDIS_QUEUE_SET_KEY, id.toString());
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
                setOps.remove(REDIS_QUEUE_SET_KEY, id.toString());
            } catch (NotFoundException ignored) {
                setOps.remove(REDIS_QUEUE_SET_KEY, id.toString());
        } catch (MojangAPIRateLimitException e) {
            listOps.rightPush(REDIS_QUEUE_KEY, item);
            try {
                Thread.sleep(150);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
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

        // Batch check which are already in the queue
        List<Object> inQueueResults = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                SetOperations<K, V> setOps = operations.opsForSet();
                for (UUID uuid : toEnqueue) {
                    setOps.isMember((K) REDIS_QUEUE_SET_KEY, (V) uuid.toString());
                }
                return null;
            }
        });

        List<SubmitQueueItem> items = new ArrayList<>();
        List<String> idsToAddToSet = new ArrayList<>();
        for (int i = 0; i < toEnqueue.size(); i++) {
            if (Boolean.TRUE.equals(inQueueResults.get(i))) {
                continue;
            }
            UUID uuid = toEnqueue.get(i);
            items.add(new SubmitQueueItem(uuid, by));
            idsToAddToSet.add(uuid.toString());
        }
        if (items.isEmpty()) {
            return;
        }

        // Batch push to list and set (2 Redis calls instead of 2 per player)
        ListOperations<String, SubmitQueueItem> listOps = submitQueueTemplate.opsForList();
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        listOps.rightPushAll(REDIS_QUEUE_KEY, items);
        setOps.add(REDIS_QUEUE_SET_KEY, idsToAddToSet.toArray());

        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", items.size(), size != null ? size : 0, submittedBy);
    }
}
