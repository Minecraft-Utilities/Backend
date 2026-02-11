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
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.List;
import java.util.Map;
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

    private final RedisTemplate<String, Object> redisTemplate;
    private final PlayerService playerService;
    private final MojangService mojangService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public PlayerSubmitService(RedisTemplate<String, Object> redisTemplate, @Lazy PlayerService playerService, @Lazy MojangService mojangService,
                               MongoTemplate mongoTemplate) {
        this.redisTemplate = redisTemplate;
        this.playerService = playerService;
        this.mojangService = mojangService;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubmitConsumer() {
        ListOperations<String, Object> listOps = redisTemplate.opsForList();
        Main.EXECUTOR.submit(() -> {
            while (true) {
                try {
                    Object raw = listOps.leftPop(REDIS_QUEUE_KEY, BLPOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (raw == null) {
                        continue;
                    }

                    Map<String, String> item = (Map<String, String>) raw;
                    UUID id = UUID.fromString(item.get("id"));
                    String byStr = item.get("by");
                    UUID submittedBy = UUIDUtils.parseUuid(byStr != null ? byStr.trim() : "");

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
                        listOps.rightPush(REDIS_QUEUE_KEY, Map.of("id", id.toString(), "by", byStr != null ? byStr : ""));
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
        ListOperations<String, Object> listOps = redisTemplate.opsForList();
        int added = 0;
        String by = submittedBy != null ? submittedBy : "";
        for (String id : players) {
            if (id == null || id.isBlank()) {
                continue;
            }

            String trimmed = id.trim();
            UUID uuid = UUIDUtils.parseUuid(trimmed);
            if (uuid == null) {
                continue;
            }
            if (this.playerService.exists(uuid)) {
                continue;
            }

            listOps.rightPush(REDIS_QUEUE_KEY, Map.of("id", trimmed, "by", by));
            added++;
        }
        Long size = listOps.size(REDIS_QUEUE_KEY);
        log.info("Submitted {} players to submit queue (total queued: {}, submittedBy: {})", added, size != null ? size : 0, submittedBy);
    }
}
