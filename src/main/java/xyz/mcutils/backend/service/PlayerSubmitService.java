package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.MojangAPIRateLimitException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;
import xyz.mcutils.backend.model.redis.SubmitQueueItem;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.List;
import java.util.UUID;
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
    private static final double SUBMIT_RATE_PER_SECOND = 200.0;
    private static final long BLPOP_TIMEOUT_SECONDS = 2;

    /** Rate limit submission so in-flight ≈ rate × task duration (avoids OOM). */
    private final RateLimiter submitRateLimiter = RateLimiter.create(SUBMIT_RATE_PER_SECOND);

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
        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    SubmitQueueItem item = listOps.leftPop(REDIS_QUEUE_KEY, BLPOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (item == null) continue;
                    submitRateLimiter.acquire();
                    Main.EXECUTOR.submit(() -> {
                        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
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
                                    Thread.sleep(2_000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                    });
                } catch (QueryTimeoutException e) {
                    // BLPOP timed out waiting for an item (empty queue) – continue
                } catch (Exception e) {
                    log.warn("Submit consumer error", e);
                }
            }
        });
    }

    /**
     * Submits a list of players to the submit queue.
     *
     * @param players the list of players to submit
     * @param submittedBy the identifier for who submitted
     */
    public void submitPlayers(List<String> players, String submittedBy) {
        ListOperations<String, SubmitQueueItem> listOps = submitQueueTemplate.opsForList();
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        int added = 0;
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;
        for (String id : players) {
            if (id == null || id.isBlank()) {
                continue;
            }

            // parse the uuid
            UUID uuid = UUIDUtils.parseUuid(id.trim());
            if (uuid == null) {
                continue;
            }
            if (this.playerService.exists(uuid)) {
                continue;
            }

            // check if the player is already in the queue
            if (Boolean.TRUE.equals(setOps.isMember(REDIS_QUEUE_SET_KEY, uuid.toString()))) {
                continue;
            }

            listOps.rightPush(REDIS_QUEUE_KEY, new SubmitQueueItem(uuid, by));
            setOps.add(REDIS_QUEUE_SET_KEY, uuid.toString());
            added++;
        }
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", added, size != null ? size : 0, submittedBy);
    }
}
