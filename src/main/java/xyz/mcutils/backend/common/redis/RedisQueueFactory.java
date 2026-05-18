package xyz.mcutils.backend.common.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates {@link RedisQueue} instances backed by the shared queue Redis template.
 */
@Component
public class RedisQueueFactory {

    private final RedisTemplate<String, String> redis;

    public RedisQueueFactory(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public RedisQueue getQueue(String queueName) {
        return new RedisQueue(redis, queueName);
    }

    public RedisQueue getQueue(String listKey, String dedupeSetKey) {
        return new RedisQueue(redis, listKey, dedupeSetKey, true);
    }

    public RedisQueue getQueueWithoutDeduplication(String listKey) {
        return new RedisQueue(redis, listKey, null, false);
    }
}
