package xyz.mcutils.backend.service.tracker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed {@link ScanFingerprintStore}. Fingerprints are stored as sets of distinct /24
 * networks (or per-IP ports) with a TTL window, so honeypot farms that replay one sample across
 * thousands of IPs become countable without unbounded storage.
 */
@Component
public class RedisScanFingerprintStore implements ScanFingerprintStore {

    private static final String FINGERPRINT_KEY = "scan:fingerprint:";
    private static final String IP_PORT_KEY = "scan:ip-ports:";

    private final RedisTemplate<String, String> redis;

    public RedisScanFingerprintStore(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public long recordFingerprint(String fingerprint, String net24, Duration ttl) {
        String key = FINGERPRINT_KEY + fingerprint;
        redis.opsForSet().add(key, net24);
        redis.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
        Long size = redis.opsForSet().size(key);
        return size == null ? 1 : size;
    }

    @Override
    public long recordIpPortFingerprint(String ip, int port, String fingerprint, Duration ttl) {
        String key = IP_PORT_KEY + ip + ":" + fingerprint;
        redis.opsForSet().add(key, Integer.toString(port));
        redis.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
        Long size = redis.opsForSet().size(key);
        return size == null ? 1 : size;
    }
}