package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerSubmitService;

public class SubmissionQueueSizeMetric extends GaugeWithCallbackMetric {
    public SubmissionQueueSizeMetric() {
        super(GaugeWithCallback.builder()
                .name("player_submission_queue_size")
                .callback(callback -> {
                    if (PlayerSubmitService.INSTANCE != null) {
                        callback.call(PlayerSubmitService.INSTANCE.getSubmissionQueueSize());
                    }
                })
                .register(MetricService.REGISTRY));
    }
}
