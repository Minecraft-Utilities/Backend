package xyz.mcutils.backend.metric.impl.player;

import xyz.mcutils.backend.metric.Metric;
import xyz.mcutils.backend.metric.MetricPoint;
import xyz.mcutils.backend.service.PlayerSubmitService;

import java.util.concurrent.TimeUnit;

public class SubmissionQueueSizeMetric extends Metric {
    private final PlayerSubmitService playerSubmitService;

    public SubmissionQueueSizeMetric(PlayerSubmitService playerSubmitService) {
        super(TimeUnit.SECONDS.toMillis(2L));
        this.playerSubmitService = playerSubmitService;
    }

    @Override
    public MetricPoint buildPoint() {
        return MetricPoint.measurement("player_submission_queue_size")
                .addField("value", this.playerSubmitService.getSubmissionQueueSize());
    }
}
