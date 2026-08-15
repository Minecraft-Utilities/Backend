package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerSubmitService;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes the player submission queue size as a gauge backed by an in-memory snapshot,
 * so scraping /metrics never issues a Redis LLEN round-trip per scrape.
 * <p>
 * The value holder is static because the registered gauge callback is built inside the
 * {@code super(...)} call, which cannot reference the not-yet-constructed instance; this class
 * is a singleton registered once in {@link MetricService}.
 */
public class SubmissionQueueSizeMetric extends GaugeWithCallbackMetric {

    private static final AtomicLong QUEUE_SIZE = new AtomicLong();

    private final PlayerSubmitService playerSubmitService;

    public SubmissionQueueSizeMetric(PlayerSubmitService playerSubmitService) {
        super(GaugeWithCallback.builder()
                .name("player_submission_queue_size")
                .callback(callback -> callback.call(QUEUE_SIZE.get()))
                .register(MetricService.REGISTRY));
        this.playerSubmitService = playerSubmitService;
    }

    /**
     * Refreshes the in-memory snapshot. Called by a scheduler, never from the scrape callback.
     */
    public void refresh() {
        QUEUE_SIZE.set(this.playerSubmitService.getSubmissionQueueSize());
    }
}
