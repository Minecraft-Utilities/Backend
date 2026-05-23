package xyz.mcutils.backend.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.FutureUtils;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.common.redis.RedisQueue;
import xyz.mcutils.backend.common.redis.RedisQueueFactory;
import xyz.mcutils.backend.exception.impl.RateLimitException;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.repository.postgres.SkinRepository;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background worker that resolves 64×32 legacy skin status for newly tracked skins.
 * Queue payloads are skin {@code textureId} strings.
 */
@SuppressWarnings("UnstableApiUsage")
@Service
@Slf4j
public class LegacySkinCheckService {

    private static final String QUEUE_NAME = "legacy-skin-check-queue";
    private static final int BATCH_SIZE = 500;
    private static final int RATE_LIMIT = 100;
    private static final Duration EMPTY_QUEUE_BLOCK = Duration.ofSeconds(2);
    private static final String SKIN_TEXTURE_CACHE = "skinByTextureId";

    private final RateLimiter rateLimiter = RateLimiter.create(RATE_LIMIT);
    private final RedisQueue checkQueue;
    private final SkinRepository skinRepository;
    private final WebRequest webRequest;
    private final CacheManager cacheManager;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public LegacySkinCheckService(RedisQueueFactory queueFactory, SkinRepository skinRepository, WebRequest webRequest, CacheManager cacheManager) {
        this.checkQueue = queueFactory.getQueue(QUEUE_NAME);
        this.skinRepository = skinRepository;
        this.webRequest = webRequest;
        this.cacheManager = cacheManager;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        running.set(false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        Main.EXECUTOR.submit(this::runConsumerLoop);
    }

    /**
     * Enqueues a skin texture for legacy status resolution.
     */
    public void enqueue(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return;
        }
        enqueue(List.of(textureId.trim()));
    }

    /**
     * Enqueues multiple skin textures, skipping any already queued.
     */
    public int enqueue(Collection<String> textureIds) {
        if (textureIds == null || textureIds.isEmpty()) {
            return 0;
        }
        List<RedisQueue.QueueItem> items = textureIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .map(RedisQueue.QueueItem::of)
                .toList();
        if (items.isEmpty()) {
            return 0;
        }
        RedisQueue.EnqueueResult result = checkQueue.enqueue(items);
        if (result.enqueued() > 0) {
            log.debug("Enqueued {} skins for legacy check (queue size: {})", result.enqueued(), checkQueue.size());
        }
        return result.enqueued();
    }

    public long getQueueSize() {
        return checkQueue.size();
    }

    private void runConsumerLoop() {
        while (running.get()) {
            try {
                Optional<List<String>> batch = checkQueue.blockingTakeBatch(BATCH_SIZE, EMPTY_QUEUE_BLOCK);
                if (batch.isEmpty()) {
                    continue;
                }
                processBatch(batch.get());
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                log.error("Legacy skin check consumer error, continuing", e);
            }
        }
    }

    private void processBatch(List<String> textureIds) {
        Queue<CheckResult> results = new ConcurrentLinkedQueue<>();
        List<Future<Void>> futures = new ArrayList<>();

        for (String textureId : textureIds) {
            if (textureId == null || textureId.isBlank()) {
                continue;
            }
            rateLimiter.acquire();
            futures.add(Main.EXECUTOR.submit(() -> {
                results.add(checkLegacy(textureId.trim()));
                return null;
            }));
        }

        FutureUtils.awaitAll(futures, "legacy-skin-check");

        List<String> toRequeue = new ArrayList<>();
        List<String> completed = new ArrayList<>();

        for (CheckResult result : results) {
            switch (result.outcome()) {
                case RETRY -> toRequeue.add(result.textureId());
                case SUCCESS -> {
                    skinRepository.updateLegacyByTextureId(result.textureId(), result.legacy());
                    completed.add(result.textureId());
                    log.debug("Resolved legacy status for skin {}: legacy={}", result.textureId(), result.legacy());
                }
            }
        }

        if (!toRequeue.isEmpty()) {
            checkQueue.requeue(toRequeue);
        }
        if (!completed.isEmpty()) {
            checkQueue.removeDedupeKeys(completed);
        }
    }

    private CheckResult checkLegacy(String textureId) {
        try {
            String textureUrl = Skin.CDN_URL.formatted(textureId);
            byte[] bytes = webRequest.request(textureUrl).asBytes();
            if (bytes == null) {
                log.debug("No response fetching skin texture {}, will retry", textureId);
                return CheckResult.retry(textureId);
            }
            BufferedImage image = ImageUtils.decodeImage(bytes);
            boolean legacy = image.getWidth() == 64 && image.getHeight() == 32;
            return CheckResult.success(textureId, legacy);
        } catch (RateLimitException e) {
            log.debug("Rate limited checking legacy status for {}, will retry", textureId);
            return CheckResult.retry(textureId);
        } catch (ResourceAccessException e) {
            log.debug("Timed out checking legacy status for {}, will retry", textureId);
            return CheckResult.retry(textureId);
        } catch (IllegalStateException e) {
            log.debug("Failed to decode skin texture {}: {}", textureId, e.getMessage());
            return CheckResult.success(textureId, false);
        } catch (Exception e) {
            log.warn("Unexpected error checking legacy status for {}", textureId, e);
            return CheckResult.retry(textureId);
        }
    }

    private void evictSkinCache(String textureId) {
        var cache = cacheManager.getCache(SKIN_TEXTURE_CACHE);
        if (cache != null) {
            cache.evict(textureId);
        }
    }

    private record CheckResult(String textureId, Outcome outcome, boolean legacy) {
        enum Outcome {
            SUCCESS,
            RETRY
        }

        static CheckResult success(String textureId, boolean legacy) {
            return new CheckResult(textureId, Outcome.SUCCESS, legacy);
        }

        static CheckResult retry(String textureId) {
            return new CheckResult(textureId, Outcome.RETRY, false);
        }
    }
}
