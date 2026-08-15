package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single shared rate limiter for all outbound Mojang API traffic (background player
 * refresh, submit queue, and on-demand refreshes). Previously each path had its own
 * limiter, so the combined egress rate against Mojang exceeded the configured budget
 * and the on-demand path had no limiter at all.
 */
@SuppressWarnings("UnstableApiUsage")
@Service
public class MojangRateLimiter {

    private final RateLimiter rateLimiter;

    public MojangRateLimiter(@Value("${mc-utils.player-refresh.mojang-rate-limit:600}") double rateLimit) {
        this.rateLimiter = RateLimiter.create(rateLimit);
    }

    /**
     * Blocks until a permit is available.
     */
    public void acquire() {
        this.rateLimiter.acquire();
    }
}
