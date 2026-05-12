package xyz.mcutils.backend.metric.impl.player;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import xyz.mcutils.backend.metric.GaugeWithCallbackMetric;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.PlayerSubmitService;

public class SubmissionQueueSizeMetric extends GaugeWithCallbackMetric {
    public SubmissionQueueSizeMetric(PlayerSubmitService playerSubmitService) {
        super(GaugeWithCallback.builder().name("player_submission_queue_size").callback(callback -> {
            callback.call(playerSubmitService.getSubmissionQueueSize());
        }).register(MetricService.REGISTRY));
    }
}
