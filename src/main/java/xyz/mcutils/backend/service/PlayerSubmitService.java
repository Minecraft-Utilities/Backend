package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
    private static final double SUBMIT_RATE_PER_SECOND = 20.0;
    private static final long BLPOP_TIMEOUT_SECONDS = 2;

    private final RateLimiter submitRateLimiter = RateLimiter.create(SUBMIT_RATE_PER_SECOND);

    private final RedisTemplate<String, SubmitQueueItem> submitQueueTemplate;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public PlayerSubmitService(RedisTemplate<String, SubmitQueueItem> submitQueueTemplate, @Lazy PlayerService playerService, @Lazy MojangService mojangService,
                               MongoTemplate mongoTemplate) {
        this.submitQueueTemplate = submitQueueTemplate;
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

                    UUID id = item.id();
                    UUID submittedBy = item.submittedBy();

                    submitRateLimiter.acquire();
                    try {
                        if (this.playerService.exists(id)) continue;

                        MojangProfileToken token = this.mojangService.getProfile(id.toString());
                        if (token == null) {
                            log.warn("Player with uuid '{}' was not found", id);
                            continue;
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
                    } catch (MojangAPIRateLimitException e) {
                        listOps.rightPush(REDIS_QUEUE_KEY, item);
                        try { 
                            Thread.sleep(2_000); 
                        } catch (InterruptedException ie) { 
                            Thread.currentThread().interrupt(); 
                        }
                    }
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
        int added = 0;
        UUID by = (submittedBy != null && !submittedBy.isBlank()) ? UUIDUtils.parseUuid(submittedBy.trim()) : null;
        for (String id : players) {
            if (id == null || id.isBlank()) {
                continue;
            }
            UUID uuid = UUIDUtils.parseUuid(id.trim());
            if (uuid == null) {
                continue;
            }
            if (this.playerService.exists(uuid)) {
                continue;
            }
            listOps.rightPush(REDIS_QUEUE_KEY, new SubmitQueueItem(uuid, by));
            added++;
        }
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", added, size != null ? size : 0, submittedBy);
    }
}
